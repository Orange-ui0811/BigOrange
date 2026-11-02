package com.orangejd.server.service.impl;

import com.orangejd.pojo.entity.Work;
import com.orangejd.pojo.entity.WorkSaleOption;
import com.orangejd.pojo.vo.WorkDetailVO;
import com.orangejd.pojo.vo.WorkVO;

import java.util.List;

final class WorkViewAssembler {

    private WorkViewAssembler() {
    }

    static WorkVO toListItem(Work work) {
        WorkVO view = new WorkVO();
        view.setId(work.getId()); view.setOwnerId(work.getOwnerId()); view.setTitle(work.getTitle());
        view.setSummary(work.getSummary()); view.setDetail(work.getDetail()); view.setCategoryId(work.getCategoryId());
        view.setTags(work.getTags()); view.setCoverUrl(work.getCoverUrl()); view.setDemoUrl(work.getDemoUrl());
        view.setVideoUrl(work.getVideoUrl()); view.setAuditStatus(work.getAuditStatus());
        view.setVisibilityStatus(work.getVisibilityStatus()); view.setPublishStatus(work.getPublishStatus());
        view.setCreateTime(work.getCreateTime()); view.setUpdateTime(work.getUpdateTime());
        view.setOwnerName(work.getOwnerName()); view.setOwnerAvatar(work.getOwnerAvatar());
        view.setFavoriteCount(work.getFavoriteCount()); view.setCommentCount(work.getCommentCount());
        view.setPrice(work.getReferencePrice());
        return view;
    }

    static WorkDetailVO toDetail(Work work, List<WorkSaleOption> options) {
        WorkDetailVO view = new WorkDetailVO();
        view.setId(work.getId()); view.setOwnerId(work.getOwnerId()); view.setTitle(work.getTitle());
        view.setSummary(work.getSummary()); view.setDetail(work.getDetail()); view.setCategoryId(work.getCategoryId());
        view.setTags(work.getTags()); view.setCoverUrl(work.getCoverUrl()); view.setDemoUrl(work.getDemoUrl());
        view.setVideoUrl(work.getVideoUrl()); view.setAuditStatus(work.getAuditStatus());
        view.setVisibilityStatus(work.getVisibilityStatus()); view.setPublishStatus(work.getPublishStatus());
        view.setCreateTime(work.getCreateTime()); view.setUpdateTime(work.getUpdateTime());
        view.setOwnerName(work.getOwnerName()); view.setOwnerAvatar(work.getOwnerAvatar());
        view.setSaleOptions(options.stream().map(WorkViewAssembler::toSaleOption).toList());
        return view;
    }

    private static WorkDetailVO.SaleOptionVO toSaleOption(WorkSaleOption option) {
        WorkDetailVO.SaleOptionVO view = new WorkDetailVO.SaleOptionVO();
        view.setId(option.getId()); view.setSaleType(option.getSaleType());
        view.setPrice(option.getPrice()); view.setDescription(option.getDescription());
        return view;
    }
}
