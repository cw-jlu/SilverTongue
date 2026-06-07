package com.silvertongue.square.service;

import com.silvertongue.square.document.PostDocument;
import com.silvertongue.square.dto.PostCreateRequest;
import com.silvertongue.square.dto.PostVO;
import com.silvertongue.square.entity.Comment;
import com.silvertongue.square.entity.Post;
import com.silvertongue.square.mapper.CommentMapper;
import com.silvertongue.square.mapper.PostMapper;
import com.silvertongue.user.entity.User;
import com.silvertongue.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostMapper postMapper;

    @Mock
    private CommentMapper commentMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private ElasticsearchOperations esTemplate;

    @InjectMocks
    private PostService postService;

    private Map<Long, User> users;

    @BeforeEach
    void setUp() {
        User author = new User();
        author.setId(10L);
        author.setNickname("Author");
        author.setAvatarUrl("author.png");

        User commenter = new User();
        commenter.setId(20L);
        commenter.setNickname("Commenter");

        User replier = new User();
        replier.setId(30L);
        replier.setNickname("Replier");

        users = Map.of(10L, author, 20L, commenter, 30L, replier);
        lenient().when(userMapper.selectById(anyLong())).thenAnswer(invocation -> users.get(invocation.getArgument(0)));
    }

    @Test
    void createShouldPersistPostAndSyncElasticsearch() {
        PostCreateRequest request = new PostCreateRequest();
        request.setContent("hello world");
        request.setClipId(88L);

        doAnswer(invocation -> {
            Post post = invocation.getArgument(0);
            post.setId(101L);
            post.setCreateTime(LocalDateTime.now());
            return 1;
        }).when(postMapper).insert(any(Post.class));

        PostVO result = postService.create(10L, request);

        ArgumentCaptor<PostDocument> documentCaptor = ArgumentCaptor.forClass(PostDocument.class);
        verify(esTemplate).save(documentCaptor.capture());
        assertEquals(101L, result.getId());
        assertEquals("Author", result.getNickname());
        assertEquals("hello world", documentCaptor.getValue().getContent());
        assertEquals("Author", documentCaptor.getValue().getNickname());
    }

    @Test
    void detailShouldAssembleTwoLevelCommentTree() {
        Post post = new Post();
        post.setId(11L);
        post.setUserId(10L);
        post.setContent("content");
        post.setLikeCount(3);
        post.setCreateTime(LocalDateTime.now());

        Comment topLevel = new Comment();
        topLevel.setId(201L);
        topLevel.setPostId(11L);
        topLevel.setUserId(20L);
        topLevel.setContent("top");
        topLevel.setCreateTime(LocalDateTime.now());

        Comment reply = new Comment();
        reply.setId(202L);
        reply.setPostId(11L);
        reply.setUserId(30L);
        reply.setParentId(201L);
        reply.setContent("reply");
        reply.setCreateTime(LocalDateTime.now());

        when(postMapper.selectById(11L)).thenReturn(post);
        when(commentMapper.selectList(any())).thenReturn(List.of(topLevel, reply));

        PostVO detail = postService.detail(11L);

        assertEquals(1, detail.getComments().size());
        assertEquals("top", detail.getComments().get(0).getContent());
        assertEquals(1, detail.getComments().get(0).getReplies().size());
        assertEquals("reply", detail.getComments().get(0).getReplies().get(0).getContent());
    }

    @Test
    void updateShouldRejectNonAuthor() {
        Post post = new Post();
        post.setId(12L);
        post.setUserId(99L);

        PostCreateRequest request = new PostCreateRequest();
        request.setContent("updated");

        when(postMapper.selectById(12L)).thenReturn(post);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> postService.update(10L, 12L, request));

        assertEquals("only the author can edit this post", exception.getMessage());
        verify(esTemplate, never()).save(any(PostDocument.class));
    }

    @Test
    void deleteShouldRemoveCommentsPostAndIndexForAuthor() {
        Post post = new Post();
        post.setId(13L);
        post.setUserId(10L);

        when(postMapper.selectById(13L)).thenReturn(post);

        postService.delete(10L, 13L);

        verify(commentMapper).delete(any());
        verify(postMapper).deleteById(13L);
        verify(esTemplate).delete("13", PostDocument.class);
    }
}
