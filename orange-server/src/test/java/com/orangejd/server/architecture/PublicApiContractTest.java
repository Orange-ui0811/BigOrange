package com.orangejd.server.architecture;

import com.orangejd.common.result.Result;
import com.orangejd.server.controller.admin.AdminUserController;
import com.orangejd.server.controller.app.AppAiResultRecordController;
import com.orangejd.server.controller.app.AppPersonaWorkshopController;
import com.orangejd.server.controller.app.AppTrendController;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicApiContractTest {

    @Test
    void personaWorkshopRoutesRemainStable() {
        assertBase(AppPersonaWorkshopController.class, "/app/persona-workshop");
        assertRoute(AppPersonaWorkshopController.class, "snapshot", RequestMethod.GET, "");
        assertRoute(AppPersonaWorkshopController.class, "publicSnapshot", RequestMethod.GET, "/public/{accountId}");
        assertRoute(AppPersonaWorkshopController.class, "avatar", RequestMethod.GET, "/avatar");
        assertRoute(AppPersonaWorkshopController.class, "createEntry", RequestMethod.POST, "/entries");
        assertRoute(AppPersonaWorkshopController.class, "updateEntry", RequestMethod.PUT, "/entries/{id}");
        assertRoute(AppPersonaWorkshopController.class, "deleteEntry", RequestMethod.DELETE, "/entries/{id}");
        assertRoute(AppPersonaWorkshopController.class, "materials", RequestMethod.GET, "/materials");
        assertRoute(AppPersonaWorkshopController.class, "acceptMaterial", RequestMethod.POST, "/materials/{id}/accept");
        assertRoute(AppPersonaWorkshopController.class, "rejectMaterial", RequestMethod.POST, "/materials/{id}/reject");
        assertRoute(AppPersonaWorkshopController.class, "updateEquipment", RequestMethod.PUT, "/equipment/{slotCode}");
        assertRoute(AppPersonaWorkshopController.class, "updateAccessoryBlueprint", RequestMethod.PUT, "/equipment/{slotCode}/blueprint");
        assertRoute(AppPersonaWorkshopController.class, "updateSettings", RequestMethod.PUT, "/settings");
    }

    @Test
    void aiRecordAndTrendRoutesRemainStable() {
        assertBase(AppAiResultRecordController.class, "/app/ai-records");
        assertRoute(AppAiResultRecordController.class, "page", RequestMethod.GET, "");
        assertRoute(AppAiResultRecordController.class, "detail", RequestMethod.GET, "/{id}");
        assertRoute(AppAiResultRecordController.class, "delete", RequestMethod.DELETE, "/{id}");

        assertBase(AppTrendController.class, "/app/trend");
        assertRoute(AppTrendController.class, "graph", RequestMethod.GET, "/graph");
        assertRoute(AppTrendController.class, "analyze", RequestMethod.POST, "/analyze");
        assertRoute(AppTrendController.class, "analyzeV2", RequestMethod.POST, "/analyze-v2");
        assertRoute(AppTrendController.class, "history", RequestMethod.GET, "/history");
        assertRoute(AppTrendController.class, "saveHistory", RequestMethod.POST, "/history/save");
        assertRoute(AppTrendController.class, "deleteHistory", RequestMethod.DELETE, "/history/{id}");
        assertRoute(AppTrendController.class, "clearHistory", RequestMethod.DELETE, "/history/clear");
    }

    @Test
    void adminUserRoutesRemainStable() {
        assertBase(AdminUserController.class, "/admin/users");
        assertRoute(AdminUserController.class, "list", RequestMethod.GET, "");
        assertRoute(AdminUserController.class, "toggleStatus", RequestMethod.PUT, "/{id}/status");
    }

    private void assertBase(Class<?> controller, String path) {
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
        assertNotNull(mapping, controller.getName() + " must retain @RequestMapping");
        assertTrue(Arrays.asList(mapping.value()).contains(path), controller.getName() + " base path changed");
    }

    private void assertRoute(Class<?> controller, String methodName, RequestMethod verb, String path) {
        Method method = Arrays.stream(controller.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing method " + controller.getName() + "#" + methodName));
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
        assertNotNull(mapping, methodName + " must retain a mapping");
        assertTrue(Arrays.asList(mapping.method()).contains(verb), methodName + " HTTP method changed");
        String actualPath = mapping.value().length == 0 ? "" : mapping.value()[0];
        assertEquals(path, actualPath, methodName + " path changed");
        assertEquals(Result.class, method.getReturnType(), methodName + " must retain the Result response envelope");
    }
}
