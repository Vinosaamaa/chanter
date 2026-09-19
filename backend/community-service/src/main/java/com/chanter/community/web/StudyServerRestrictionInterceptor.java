package com.chanter.community.web;

import com.chanter.common.auth.AuthRequestAttributes;
import com.chanter.common.auth.ModerationAccess;
import com.chanter.common.auth.ModerationAccess.Target;
import com.chanter.community.application.CourseRepository;
import com.chanter.community.application.OfficeHoursRepository;
import com.chanter.community.application.StudyServerRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Applies the same live server restriction to the concrete public community scope routes.
 * Uses Spring's matched route variables, never a caller-supplied parent scope or raw path parsing.
 * Existing controller/service membership and role authorization still runs after this check.
 */
@Configuration
public class StudyServerRestrictionInterceptor implements HandlerInterceptor, WebMvcConfigurer {
    private final CourseRepository courses;
    private final StudyServerRepository servers;
    private final OfficeHoursRepository officeHours;
    private final ModerationAccess moderation;

    public StudyServerRestrictionInterceptor(CourseRepository courses, StudyServerRepository servers,
            OfficeHoursRepository officeHours, ModerationAccess moderation) {
        this.courses=courses; this.servers=servers; this.officeHours=officeHours; this.moderation=moderation;
    }

    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/api/v1/**").excludePathPatterns("/api/v1/internal/**");
    }

    @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        var user=(UUID)request.getAttribute(AuthRequestAttributes.USER_ID);
        if(user==null) return true; // Authentication owns rejection of public requests without identity.
        Object matched=request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if(matched==null) return true;
        @SuppressWarnings("unchecked") var variables=(Map<String,String>)request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if(variables==null) return true;
        String route=matched.toString();
        UUID server=null;
        try {
            if(route.startsWith("/api/v1/study-servers/{")) {
                server=UUID.fromString(variables.getOrDefault("studyServerId",variables.get("id")));
            } else if(route.startsWith("/api/v1/courses/{courseId}")) {
                server=required(courses.findStudyServerIdByCourseId(id(variables,"courseId")));
            } else if(route.startsWith("/api/v1/cohorts/{cohortId}")) {
                server=serverForCohort(id(variables,"cohortId"));
            } else if(route.startsWith("/api/v1/course-channels/{channelId}")) {
                var channel=courses.findActiveChannelById(id(variables,"channelId"));
                server=required(channel.flatMap(value -> courses.findStudyServerIdByCourseId(value.courseId())));
            } else if(route.startsWith("/api/v1/study-server-channels/{channelId}")) {
                server=required(servers.findChannelById(id(variables,"channelId")).map(value -> value.studyServerId()));
            } else if(route.startsWith("/api/v1/office-hours/{sessionId}")) {
                server=serverForCohort(required(officeHours.findSessionById(id(variables,"sessionId")).map(value -> value.cohortId())));
            }
        } catch(IllegalArgumentException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid community scope reference");
        }
        if(server!=null) moderation.requireAllowed(user,List.of(new Target("STUDY_SERVER",server)));
        return true;
    }

    private UUID serverForCohort(UUID cohort) {
        return required(courses.findCourseIdByCohortId(cohort).flatMap(courses::findStudyServerIdByCourseId));
    }
    private static UUID id(Map<String,String> variables,String key) { return UUID.fromString(variables.get(key)); }
    private static UUID required(Optional<UUID> value) {
        return value.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Community scope not found"));
    }
}
