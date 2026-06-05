package com.silvertongue.square.controller;

import com.silvertongue.common.ApiResult;
import com.silvertongue.security.AuthenticatedUser;
import com.silvertongue.square.dto.CommentCreateRequest;
import com.silvertongue.square.dto.CommentVO;
import com.silvertongue.square.dto.PostCreateRequest;
import com.silvertongue.square.dto.PostVO;
import com.silvertongue.square.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/post")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    /** 发帖 */
    @PostMapping
    public ApiResult<PostVO> create(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody PostCreateRequest request
    ) {
        return ApiResult.success(postService.create(currentUser.getUserId(), request));
    }

    /** 帖子列表 */
    @GetMapping("/list")
    public ApiResult<List<PostVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResult.success(postService.list(page, size));
    }

    /** 帖子详情（含评论） */
    @GetMapping("/{id}")
    public ApiResult<PostVO> detail(@PathVariable Long id) {
        return ApiResult.success(postService.detail(id));
    }

    /** 点赞 */
    @PostMapping("/{id}/like")
    public ApiResult<Void> like(@PathVariable Long id) {
        postService.like(id);
        return ApiResult.success();
    }

    /** 评论 */
    @PostMapping("/comment")
    public ApiResult<CommentVO> comment(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody CommentCreateRequest request
    ) {
        return ApiResult.success(postService.comment(currentUser.getUserId(), request));
    }

    /** ES 全文检索 */
    @GetMapping("/search")
    public ApiResult<List<PostVO>> search(
            @RequestParam("q") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResult.success(postService.search(keyword, page, size));
    }
}
