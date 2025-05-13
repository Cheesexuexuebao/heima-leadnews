package com.heima.wemedia.service.impl;


import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.common.utils.StringUtils;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.common.constants.WemediaConstants;
import com.heima.common.exception.CustomException;
import com.heima.model.common.dtos.PageResponseResult;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.wemedia.dtos.WmNewsDto;
import com.heima.model.wemedia.dtos.WmNewsPageReqDto;
import com.heima.model.wemedia.pojos.WmMaterial;
import com.heima.model.wemedia.pojos.WmNews;
import com.heima.model.wemedia.pojos.WmNewsMaterial;
import com.heima.utils.thread.WmThreadLocalUtil;
import com.heima.wemedia.mapper.WmMaterialMapper;
import com.heima.wemedia.mapper.WmNewsMapper;
import com.heima.wemedia.mapper.WmNewsMaterialMapper;
import com.heima.wemedia.service.WmNewsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.alibaba.cloud.commons.lang.StringUtils.*;

@Service
@Slf4j
@Transactional
public class WmNewsServiceImpl  extends ServiceImpl<WmNewsMapper, WmNews> implements WmNewsService {


    @Override
    public ResponseResult findList(WmNewsPageReqDto dto) {
        dto.checkParam();
        IPage page = new Page(dto.getPage(), dto.getSize());
        LambdaQueryWrapper<WmNews> wrapper = new LambdaQueryWrapper<>();
        if(dto.getStatus() != null){
            lambdaQuery().eq(WmNews::getStatus,dto.getStatus());
        }
        if(dto.getChannelId() != null){
            lambdaQuery().eq(WmNews::getChannelId,dto.getChannelId());
        }
        if(dto.getBeginPubDate() != null && dto.getEndPubDate() != null){
            lambdaQuery().between(WmNews::getPublishTime, dto.getBeginPubDate(), dto.getEndPubDate());
        }
        if(isNotBlank(dto.getKeyword())){
            lambdaQuery().like(WmNews::getTitle,dto.getKeyword());
        }

        wrapper.eq(WmNews::getUserId, WmThreadLocalUtil.getUser().getId());
        wrapper.orderByDesc(WmNews::getPublishTime);
        page = page(page, wrapper);

        ResponseResult result = new PageResponseResult(dto.getPage(), dto.getSize(),(int)page.getTotal());

        result.setData(page.getRecords());

        return result;
    }

    @Override
    public ResponseResult submitNews(WmNewsDto dto) {
        //条件判断
        if(dto == null || dto.getContent() == null){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }

        //保存或修改文章
        WmNews wmNews = new WmNews();
        BeanUtils.copyProperties(dto, wmNews);

        if(dto.getImages() != null && dto.getImages().size() > 0){
            String ImageStr = StringUtils.join(dto.getImages(), ",");
            wmNews.setImages(ImageStr);
        }
        if(dto.getType().equals(WemediaConstants.WM_NEWS_TYPE_AUTO)){
            wmNews.setType(null);
        }

        saveOrUpdateWmNew(wmNews);
        //判断是否为草稿
        if(dto.getStatus().equals(WmNews.Status.NORMAL.getCode())){
            return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
        }

        //保存文章内容图片与素材关系
        List<String> materials = ectractUrlInfo(dto.getContent());
        saveRelativeInfoForContent(materials, wmNews.getId());
        //保存文章封面图片与素材关系
        saveRelativeInfoForCover(dto, wmNews, materials);
        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
    }

    private void saveRelativeInfoForCover(WmNewsDto dto, WmNews wmNews, List<String> materials) {

        List<String> images = dto.getImages();

        if(dto.getType().equals(WemediaConstants.WM_NEWS_TYPE_AUTO)){
            if(materials.size() >= 3){
                wmNews.setType(WemediaConstants.WM_NEWS_MANY_IMAGE);
                images = materials.stream().limit(3).collect(Collectors.toList());
            }else if(materials.size() >= 1 && materials.size() < 3){
                wmNews.setType(WemediaConstants.WM_NEWS_SINGLE_IMAGE);
                images = materials.stream().limit(1).collect(Collectors.toList());
            }else{
                wmNews.setType(WemediaConstants.WM_NEWS_NONE_IMAGE);
            }
            if(images != null && images.size() > 0){
                wmNews.setImages(StringUtils.join(images, ","));
            }
            updateById(wmNews);
        }

        if(images != null && images.size() > 0){
            saveRelativeInfo(images, wmNews.getId(), WemediaConstants.WM_COVER_REFERENCE);
        }
    }

    private void saveRelativeInfoForContent(List<String> materials, Integer newsId) {
        saveRelativeInfo(materials, newsId, WemediaConstants.WM_CONTENT_REFERENCE);
    }

    @Autowired
    private WmMaterialMapper wmMaterialMapper;

    private void saveRelativeInfo(List<String> materials, Integer newsId, Short type) {
        if(materials != null && !materials.isEmpty()){
            //url -> 素材id
            List<WmMaterial> dbMaterials = wmMaterialMapper.selectList(Wrappers.<WmMaterial>lambdaQuery().in(WmMaterial::getUrl, materials));

            //判断素材是否有效
            if(dbMaterials == null || dbMaterials.size() == 0){
                //手动抛出异常  第一个功能：提示调用者素材失效  第二个功能：进行数据的回滚
                throw new CustomException(AppHttpCodeEnum.MATERIALS_REFERENCE_FAIL);
            }

            if(materials.size() != dbMaterials.size()){
                throw new CustomException(AppHttpCodeEnum.MATERIALS_REFERENCE_FAIL);
            }

            List<Integer> idList = dbMaterials.stream().map(WmMaterial::getId).collect(Collectors.toList());

            //批量保存
            wmNewsMaterialMapper.saveRelations(idList, newsId, type);
        }
    }

    private List<String> ectractUrlInfo(String content) {
        List<String> list = new ArrayList<>();
        List<Map> maps = JSON.parseArray(content, Map.class);
        for (Map map : maps) {
            if(map.get("type").equals("image")){
                String imgUrl = (String)map.get("value");
                list.add(imgUrl);
            }
        }
        return list;
    }

    @Autowired
    private WmNewsMaterialMapper wmNewsMaterialMapper;

    private void saveOrUpdateWmNew(WmNews wmNews) {
        //补全属性
        wmNews.setUserId(WmThreadLocalUtil.getUser().getId());
        wmNews.setCreatedTime(new Date());
        wmNews.setSubmitedTime(new Date());
        wmNews.setEnable((short)1);

        if(wmNews.getId() == null){
            save(wmNews);
        }
        else{
            wmNewsMaterialMapper.delete(Wrappers.<WmNewsMaterial>lambdaQuery().eq(WmNewsMaterial::getNewsId,wmNews.getId()));
            updateById(wmNews);
        }
    }
}
