package com.silvertongue.square.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.silvertongue.square.entity.Comment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CommentMapper extends BaseMapper<Comment> {
}
