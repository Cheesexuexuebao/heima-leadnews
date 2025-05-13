package com.heima.article.controller.v1;

import com.heima.article.service.ApArticleService;
import com.heima.common.constants.ArticleConstants;
import com.heima.model.article.dtos.ArticleHomeDto;
import com.heima.model.common.dtos.ResponseResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/article")
@Api(value = "文章加载",tags = "文章加载")
public class ArticleHomeController {


    @Autowired
    ApArticleService apArticleService;
    /**
     * 加载首页
     * @param dto
     * @return
     */
    @PostMapping("load")
    @ApiOperation("文章加载")
    public ResponseResult load(@RequestBody ArticleHomeDto dto){
            return apArticleService.load(dto, ArticleConstants.LOADTYPE_LOAD_MORE);
    }

    /**
     * 加载更多
     * @param dto
     * @return
     */
    @PostMapping("loadmore")
    @ApiOperation("更多文章加载")
    public ResponseResult loadmore(@RequestBody ArticleHomeDto dto){
        return apArticleService.load(dto, ArticleConstants.LOADTYPE_LOAD_MORE);
    }
    /**
     * 加载最新
     * @param dto
     * @return
     */
    @PostMapping("loadnew")
    @ApiOperation("新文章加载")
    public ResponseResult loadnew(@RequestBody ArticleHomeDto dto){
        return apArticleService.load(dto, ArticleConstants.LOADTYPE_LOAD_NEW);
    }
}
