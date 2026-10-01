package org.schoolmela.quiz.group;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.schoolmela.quiz.IntegrationTest;

class GroupIntegrationTest extends IntegrationTest {

    private static String uniqueName(String prefix) {
        return prefix + " " + UUID.randomUUID().toString().substring(0, 8);
    }

    private long student(String name) throws Exception {
        String body = register(name, uniqueMobile(), "4321").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.user.id")).longValue();
    }

    private long group(String name) throws Exception {
        return idOf(asAdmin(post("/admin/groups"), "{\"name\": \"" + name + "\"}").andExpect(status().isCreated()));
    }

    @Test
    void createsGroupsWithUniqueNames() throws Exception {
        String name = uniqueName("Class 7A");
        group(name);

        asAdmin(post("/admin/groups"), "{\"name\": \"" + name.toUpperCase() + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").value("A group with this name already exists"));
        asAdmin(post("/admin/groups"), "{\"name\": \"  \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").value("Please give the group a name"));
    }

    @Test
    void renamesAGroupButNotToAnotherGroupsName() throws Exception {
        String taken = uniqueName("Taken");
        group(taken);
        long id = group(uniqueName("Old"));
        String renamed = uniqueName("New");

        asAdmin(put("/admin/groups/{id}", id), "{\"name\": \"" + renamed + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(renamed));
        // Keeping its own name (different case) is fine.
        asAdmin(put("/admin/groups/{id}", id), "{\"name\": \"" + renamed.toUpperCase() + "\"}")
                .andExpect(status().isOk());
        asAdmin(put("/admin/groups/{id}", id), "{\"name\": \"" + taken + "\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void addsAndRemovesMembers() throws Exception {
        long id = group(uniqueName("Members"));
        long asha = student("Asha");
        long ravi = student("Ravi");

        asAdmin(post("/admin/groups/{id}/members", id), "{\"studentIds\": [%d, %d, %d]}".formatted(ravi, asha, asha))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members[*].name").value(contains("Asha", "Ravi")));
        // Adding an existing member again changes nothing.
        asAdmin(post("/admin/groups/{id}/members", id), "{\"studentIds\": [%d]}".formatted(asha))
                .andExpect(jsonPath("$.members.length()").value(2));

        asAdmin(delete("/admin/groups/{id}/members/{studentId}", id, asha))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members[*].name").value(contains("Ravi")));

        asAdmin(get("/admin/groups"))
                .andExpect(jsonPath("$[?(@.id == %d)].memberCount".formatted(id)).value(contains(1)));
    }

    @Test
    void onlyStudentsCanBeMembers() throws Exception {
        long id = group(uniqueName("Students only"));
        long adminId = ((Number) JsonPath.read(login(ADMIN_MOBILE, ADMIN_PIN).andReturn().getResponse()
                .getContentAsString(), "$.user.id")).longValue();

        asAdmin(post("/admin/groups/{id}/members", id), "{\"studentIds\": [%d]}".formatted(adminId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.studentIds").exists());
        asAdmin(post("/admin/groups/{id}/members", id), "{\"studentIds\": [999999]}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletesAGroup() throws Exception {
        long id = group(uniqueName("Gone"));

        asAdmin(delete("/admin/groups/{id}", id)).andExpect(status().isNoContent());

        asAdmin(get("/admin/groups/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
        asAdmin(get("/admin/groups")).andExpect(jsonPath("$[*].id").value(not(hasItem((int) id))));
    }
}
