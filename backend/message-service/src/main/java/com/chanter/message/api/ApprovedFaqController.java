package com.chanter.message.api;

import com.chanter.common.ServiceInfo;
import com.chanter.common.auth.AuthRequestAttributes;
import com.chanter.message.application.ApprovedFaqService;
import com.chanter.message.application.FaqCandidateGroup;
import com.chanter.message.domain.ApprovedFaq;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping(ServiceInfo.API_V1_PREFIX)
public class ApprovedFaqController {

    private final ApprovedFaqService approvedFaqService;

    public ApprovedFaqController(ApprovedFaqService approvedFaqService) {
        this.approvedFaqService = approvedFaqService;
    }

    @GetMapping("/course-channels/{channelId}/faq-candidates")
    public FaqCandidateListResponse listFaqCandidates(
            @PathVariable UUID channelId,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID viewerUserId
    ) {
        List<FaqCandidateGroupResponse> faqCandidates = approvedFaqService
                .listFaqCandidates(channelId, viewerUserId)
                .stream()
                .map(FaqCandidateGroupResponse::from)
                .toList();

        return new FaqCandidateListResponse(faqCandidates);
    }

    @PostMapping("/courses/{courseId}/approved-faqs")
    public ResponseEntity<ApprovedFaqResponse> createOrUpdateApprovedFaq(
            @PathVariable UUID courseId,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID approvedByUserId,
            @Valid @RequestBody UpsertApprovedFaqRequest request
    ) {
        ApprovedFaq approvedFaq = approvedFaqService.createOrUpdateApprovedFaq(
                courseId,
                request.channelId(),
                approvedByUserId,
                request.id(),
                request.question(),
                request.answer(),
                request.sourceSupportQuestionIds()
        );

        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(ServiceInfo.API_V1_PREFIX + "/courses/{courseId}/approved-faqs/{faqId}")
                .buildAndExpand(courseId, approvedFaq.id())
                .toUri();

        return ResponseEntity.created(location).body(ApprovedFaqResponse.from(approvedFaq));
    }

    @GetMapping("/courses/{courseId}/approved-faqs")
    public ApprovedFaqListResponse listApprovedFaqs(
            @PathVariable UUID courseId,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID viewerUserId
    ) {
        List<ApprovedFaqResponse> approvedFaqs = approvedFaqService
                .listApprovedFaqs(courseId, viewerUserId)
                .stream()
                .map(ApprovedFaqResponse::from)
                .toList();

        return new ApprovedFaqListResponse(approvedFaqs);
    }

    @GetMapping("/courses/{courseId}/approved-faqs/search")
    public ApprovedFaqListResponse searchApprovedFaqs(
            @PathVariable UUID courseId,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID viewerUserId,
            @RequestParam String query
    ) {
        List<ApprovedFaqResponse> approvedFaqs = approvedFaqService
                .searchApprovedFaqs(courseId, viewerUserId, query)
                .stream()
                .map(ApprovedFaqResponse::from)
                .toList();

        return new ApprovedFaqListResponse(approvedFaqs);
    }
}
