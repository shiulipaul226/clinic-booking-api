package com.clinic.booking;

import com.clinic.booking.domain.Doctor;
import com.clinic.booking.domain.Slot;
import com.clinic.booking.repository.DoctorRepository;
import com.clinic.booking.repository.SlotRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end tests through the real HTTP layer (security, validation, error format and business rules). */
class BookingFlowTest extends AbstractIntegrationTest {

    private static final String AUTH = HttpHeaders.AUTHORIZATION;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired DoctorRepository doctors;
    @Autowired SlotRepository slots;
    @Autowired Clock clock;

    @Value("${app.admin.email}") String adminEmail;
    @Value("${app.admin.password}") String adminPassword;

    // ------------------------------------------------------------------ tests

    @Test
    void fullLifecycle_book_replay_conflict_reschedule_cancel() throws Exception {
        String admin = adminToken();
        String alice = registerPatient("Alice");
        String bob = registerPatient("Bob");
        long doctorId = createDoctor(admin);
        List<Long> slotIds = createSlots(admin, doctorId);

        // public availability listing: all 4 slots are free
        mvc.perform(get("/api/doctors/{id}/slots", doctorId)
                        .param("date", tomorrow().toString()).param("onlyAvailable", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));

        // Alice books slot 0
        MvcResult booked = book(alice, slotIds.get(0), "key-1")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("BOOKED"))
                .andReturn();
        long firstId = idOf(booked);

        // Same Idempotency-Key again (e.g. client retry) -> same appointment, nothing new is booked
        book(alice, slotIds.get(0), "key-1")
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.id").value(firstId));

        // Bob wants the same slot -> 409
        book(bob, slotIds.get(0), null).andExpect(status().isConflict());

        // Alice moves to slot 1 (new appointment id, old one cancelled)
        MvcResult moved = mvc.perform(post("/api/appointments/{id}/reschedule", firstId)
                        .header(AUTH, bearer(alice)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("newSlotId", slotIds.get(1)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slotId").value(slotIds.get(1)))
                .andReturn();
        long secondId = idOf(moved);
        assertThat(secondId).isNotEqualTo(firstId);

        // slot 0 was released by the reschedule, so Bob can now have it
        book(bob, slotIds.get(0), null).andExpect(status().isCreated());

        // Alice's history: the cancelled original + the new booking
        mvc.perform(get("/api/appointments/me").header(AUTH, bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // Bob must not be able to cancel Alice's appointment (and must not learn that it exists)
        mvc.perform(post("/api/appointments/{id}/cancel", secondId).header(AUTH, bearer(bob)))
                .andExpect(status().isNotFound());

        // Alice cancels; cancelling twice is harmless
        mvc.perform(post("/api/appointments/{id}/cancel", secondId).header(AUTH, bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(post("/api/appointments/{id}/cancel", secondId).header(AUTH, bearer(alice)))
                .andExpect(status().isOk());

        // slot 0 is Bob's; slots 1, 2, 3 are free again
        mvc.perform(get("/api/doctors/{id}/slots", doctorId)
                        .param("date", tomorrow().toString()).param("onlyAvailable", "true"))
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void failedRescheduleKeepsTheOriginalAppointment() throws Exception {
        String admin = adminToken();
        String alice = registerPatient("Alice");
        String bob = registerPatient("Bob");
        long doctorId = createDoctor(admin);
        List<Long> slotIds = createSlots(admin, doctorId);

        long aliceAppointment = idOf(book(alice, slotIds.get(0), null).andExpect(status().isCreated()).andReturn());
        book(bob, slotIds.get(1), null).andExpect(status().isCreated());

        // Alice tries to move into Bob's slot -> 409, and her original booking must be untouched
        mvc.perform(post("/api/appointments/{id}/reschedule", aliceAppointment)
                        .header(AUTH, bearer(alice)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("newSlotId", slotIds.get(1)))))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/appointments/me").header(AUTH, bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("BOOKED"));
    }

    @Test
    void securityRules() throws Exception {
        String patient = registerPatient("Pat");
        String admin = adminToken();

        // no token -> 401
        mvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON).content("{\"slotId\":1}"))
                .andExpect(status().isUnauthorized());
        // garbage token -> 401
        mvc.perform(get("/api/appointments/me").header(AUTH, "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
        // patient trying admin things -> 403
        mvc.perform(post("/api/doctors").header(AUTH, bearer(patient)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "X", "specialization", "Y"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/appointments").header(AUTH, bearer(patient)))
                .andExpect(status().isForbidden());
        // admin cannot book as a patient -> 403
        mvc.perform(post("/api/appointments").header(AUTH, bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotId\":1}"))
                .andExpect(status().isForbidden());
        // admin can see everything
        mvc.perform(get("/api/admin/appointments").header(AUTH, bearer(admin)))
                .andExpect(status().isOk());
        // browsing doctors is public
        mvc.perform(get("/api/doctors")).andExpect(status().isOk());
    }

    @Test
    void cannotCancelInsideTheCancellationWindow() throws Exception {
        Doctor doctor = doctors.save(new Doctor("Dr. Soon", "General"));
        LocalDateTime start = LocalDateTime.now(clock).plusHours(1).withNano(0); // window is 2 hours
        Slot slot = slots.save(new Slot(doctor, start, start.plusMinutes(30)));
        String patient = registerPatient("Late");

        long id = idOf(book(patient, slot.getId(), null).andExpect(status().isCreated()).andReturn());

        mvc.perform(post("/api/appointments/{id}/cancel", id).header(AUTH, bearer(patient)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void cannotBookASlotThatAlreadyStarted() throws Exception {
        Doctor doctor = doctors.save(new Doctor("Dr. Past", "General"));
        LocalDateTime start = LocalDateTime.now(clock).minusHours(1).withNano(0);
        Slot slot = slots.save(new Slot(doctor, start, start.plusMinutes(30)));

        book(registerPatient("Early"), slot.getId(), null).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void cannotRescheduleToADifferentDoctor() throws Exception {
        LocalDateTime start = LocalDateTime.now(clock).plusDays(1).withNano(0);
        Slot first = slots.save(new Slot(doctors.save(new Doctor("Dr. One", "General")), start, start.plusMinutes(30)));
        Slot other = slots.save(new Slot(doctors.save(new Doctor("Dr. Two", "General")), start, start.plusMinutes(30)));
        String patient = registerPatient("Switcher");

        long id = idOf(book(patient, first.getId(), null).andExpect(status().isCreated()).andReturn());

        mvc.perform(post("/api/appointments/{id}/reschedule", id)
                        .header(AUTH, bearer(patient)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("newSlotId", other.getId()))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void registrationValidationAndLoginErrors() throws Exception {
        // invalid payload -> 400 with per-field errors
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "", "email", "not-an-email", "password", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());

        // duplicate email -> 409
        String email = "dup-" + UUID.randomUUID() + "@test.com";
        String body = json(Map.of("name", "Dup", "email", email, "password", "Passw0rd!"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());

        // wrong password -> 401
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", "wrong-password"))))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ helpers

    private LocalDate tomorrow() {
        return LocalDate.now(clock).plusDays(1);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private long idOf(MvcResult result) throws Exception {
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private String registerPatient(String name) throws Exception {
        String email = name.toLowerCase() + "-" + UUID.randomUUID() + "@test.com";
        MvcResult result = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "email", email, "password", "Passw0rd!"))))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }

    private String adminToken() throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", adminEmail, "password", adminPassword))))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }

    private long createDoctor(String adminToken) throws Exception {
        MvcResult result = mvc.perform(post("/api/doctors").header(AUTH, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Dr. Test", "specialization", "General"))))
                .andExpect(status().isCreated())
                .andReturn();
        return idOf(result);
    }

    /** Creates four 30-minute slots (09:00-11:00) tomorrow and returns their ids in time order. */
    private List<Long> createSlots(String adminToken, long doctorId) throws Exception {
        Map<String, Object> request = Map.of(
                "date", tomorrow().toString(), "startTime", "09:00", "endTime", "11:00", "slotMinutes", 30);
        MvcResult result = mvc.perform(post("/api/doctors/{id}/slots", doctorId)
                        .header(AUTH, bearer(adminToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(4))
                .andReturn();
        List<Integer> ids = JsonPath.read(result.getResponse().getContentAsString(), "$[*].id");
        return ids.stream().map(Number::longValue).toList();
    }

    private ResultActions book(String token, long slotId, String idempotencyKey) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/appointments")
                .header(AUTH, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("slotId", slotId)));
        if (idempotencyKey != null) {
            request = request.header("Idempotency-Key", idempotencyKey);
        }
        return mvc.perform(request);
    }
}
