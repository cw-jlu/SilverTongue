package com.silvertongue.square.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.silvertongue.square.document.PostDocument;
import com.silvertongue.square.dto.CommentCreateRequest;
import com.silvertongue.square.dto.CommentVO;
import com.silvertongue.square.dto.PostCreateRequest;
import com.silvertongue.square.dto.PostVO;
import com.silvertongue.square.entity.Comment;
import com.silvertongue.square.entity.Post;
import com.silvertongue.square.mapper.CommentMapper;
import com.silvertongue.square.mapper.PostMapper;
import com.silvertongue.user.entity.User;
import com.silvertongue.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostService {

    private final PostMapper postMapper;
    private final CommentMapper commentMapper;
    private final UserMapper userMapper;
    private final ElasticsearchRestTemplate esTemplate;

    /**
     * 发帖 + 同步 ES 索引
     */
    @Transactional
    public PostVO create(Long userId, PostCreateRequest request) {
        Post post = new Post();
        post.setUserId(userId);
        post.setContent(request.getContent());
        post.setClipId(request.getClipId());
        post.setLikeCount(0);
        post.setCreateTime(LocalDateTime.now());
        postMapper.insert(post);

        // 同步 ES
        User user = userMapper.selectById(userId);
        PostDocument doc = PostDocument.builder()
                .postId(post.getId())
                .content(post.getContent())
                .nickname(user != null ? user.getNickname() : "unknown")
                .build();
        esTemplate.save(doc);

        return toVO(post, userId);
    }

    /**
     * 分页查询帖子列表
     */
    public List<PostVO> list(int page, int size) {
        IPage<Post> postPage = postMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<Post>().orderByDesc(Post::getCreateTime));

        return postPage.getRecords().stream()
                .map(p -> toVO(p, null))
                .collect(Collectors.toList());
    }

    /**
     * 帖子详情（含二级树形评论）
     */
    public PostVO detail(Long postId) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new IllegalArgumentException("post not found");
        }
        PostVO vo = toVO(post, null);

        // 加载所有评论
        List<Comment> comments = commentMapper.selectList(
                new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getPostId, postId)
                        .orderByAsc(Comment::getCreateTime));

        // 转换为 VO
        List<CommentVO> allVOs = comments.stream().map(this::toCommentVO).collect(Collectors.toList());

        // 按 parentId 分组组装二级树
        Map<Long, List<CommentVO>> childrenMap = allVOs.stream()
                .filter(c -> c.getParentId() != null)
                .collect(Collectors.groupingBy(CommentVO::getParentId));

        List<CommentVO> topLevel = new ArrayList<>();
        for (CommentVO c : allVOs) {
            if (c.getParentId() == null) {
                c.setReplies(childrenMap.getOrDefault(c.getId(), List.of()));
                topLevel.add(c);
            }
        }
        vo.setComments(topLevel);

        return vo;
    }

    /**
     * 编辑帖子（仅作者可编辑）+ 同步 ES
     */
    @Transactional
    public PostVO update(Long userId, Long postId, PostCreateRequest request) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new IllegalArgumentException("post not found");
        }
        if (!post.getUserId().equals(userId)) {
            throw new IllegalArgumentException("only the author can edit this post");
        }
        post.setContent(request.getContent());
        post.setClipId(request.getClipId());
        postMapper.updateById(post);

        // 同步 ES
        User user = userMapper.selectById(userId);
        PostDocument doc = PostDocument.builder()
                .postId(post.getId())
                .content(post.getContent())
                .nickname(user != null ? user.getNickname() : "unknown")
                .build();
        esTemplate.save(doc);

        return toVO(post, userId);
    }

    /**
     * 删除帖子（仅作者可删除）+ 级联删除评论 + 移除 ES 文档
     */
    @Transactional
    public void delete(Long userId, Long postId) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new IllegalArgumentException("post not found");
        }
        if (!post.getUserId().equals(userId)) {
            throw new IllegalArgumentException("only the author can delete this post");
        }
        // 级联删除评论
        commentMapper.delete(new LambdaUpdateWrapper<Comment>()
                .eq(Comment::getPostId, postId));
        postMapper.deleteById(postId);
        // 移除 ES 文档
        esTemplate.delete(String.valueOf(postId), PostDocument.class);
    }

    /**
     * 点赞
     */
    @Transactional
    public void like(Long postId) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new IllegalArgumentException("post not found");
        }
        post.setLikeCount(post.getLikeCount() + 1);
        postMapper.updateById(post);
    }

    /**
     * 发表评论（支持二级回复）
     */
    @Transactional
    public CommentVO comment(Long userId, CommentCreateRequest request) {
        Post post = postMapper.selectById(request.getPostId());
        if (post == null) {
            throw new IllegalArgumentException("post not found");
        }

        Comment comment = new Comment();
        comment.setPostId(request.getPostId());
        comment.setUserId(userId);
        comment.setParentId(request.getParentId());
        comment.setContent(request.getContent());
        comment.setCreateTime(LocalDateTime.now());
        commentMapper.insert(comment);

        return toCommentVO(comment);
    }

    /**
     * ES 全文检索（保留相关性评分排序）
     */
    public List<PostVO> search(String keyword, int page, int size) {
        Criteria criteria = new Criteria("content").matches(keyword);
        var query = new CriteriaQuery(criteria).setPageable(org.springframework.data.domain.PageRequest.of(page - 1, size));
        var searchHits = esTemplate.search(query, PostDocument.class);

        List<Long> postIds = searchHits.getSearchHits().stream()
                .map(h -> h.getContent().getPostId())
                .collect(Collectors.toList());
        if (postIds.isEmpty()) return List.of();

        // 批量查 MySQL，再按 ES 返回的相关性顺序重排
        Map<Long, Post> postMap = postMapper.selectBatchIds(postIds).stream()
                .collect(Collectors.toMap(Post::getId, p -> p));
        return postIds.stream()
                .map(postMap::get)
                .filter(Objects::nonNull)
                .map(p -> toVO(p, null))
                .collect(Collectors.toList());
    }

    private PostVO toVO(Post post, Long currentUserId) {
        User user = userMapper.selectById(post.getUserId());
        return PostVO.builder()
                .id(post.getId())
                .userId(post.getUserId())
                .nickname(user != null ? user.getNickname() : "unknown")
                .avatarUrl(user != null ? user.getAvatarUrl() : null)
                .content(post.getContent())
                .clipId(post.getClipId())
                .likeCount(post.getLikeCount())
                .createTime(post.getCreateTime())
                .build();
    }

    private CommentVO toCommentVO(Comment comment) {
        User user = userMapper.selectById(comment.getUserId());
        return CommentVO.builder()
                .id(comment.getId())
                .postId(comment.getPostId())
                .userId(comment.getUserId())
                .nickname(user != null ? user.getNickname() : "unknown")
                .avatarUrl(user != null ? user.getAvatarUrl() : null)
                .parentId(comment.getParentId())
                .content(comment.getContent())
                .createTime(comment.getCreateTime())
                .build();
    }
}
