package com.chanter.community.domain;

public record CourseCapabilities(
        boolean instructor,
        boolean teachingAssistant,
        boolean enrolled,
        boolean canManageCourse,
        boolean canManageQuestions,
        boolean canApproveFaq,
        boolean canManageTaQueue,
        boolean canUploadResources,
        boolean canScheduleOfficeHours,
        boolean canManagePeople
) {
}
