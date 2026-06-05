package com.silvertongue.square.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.silvertongue.square.entity.Post;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PostMapper extends BaseMapper<Post> {
}
