package com.orangejd.server.service;

import com.orangejd.common.context.BaseContext;
import com.orangejd.common.result.PageResult;
import com.orangejd.pojo.entity.Work;
import com.orangejd.pojo.vo.AccountVO;
import com.orangejd.pojo.vo.UserProfileVO;
import com.orangejd.pojo.vo.WorkVO;
import com.orangejd.server.mapper.AccountMapper;
import com.orangejd.server.mapper.UserProfileMapper;
import com.orangejd.server.mapper.WorkMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProfileService implements IProfileService {

    @Autowired private AccountMapper accountMapper;
    @Autowired private UserProfileMapper userProfileMapper;
    @Autowired private WorkMapper workMapper;

    public AccountVO getAuthorProfile(Long accountId) {
        com.orangejd.pojo.entity.Account account = accountMapper.selectById(accountId);
        if (account == null) return null;
        AccountVO vo = new AccountVO();
        vo.setId(account.getId());
        vo.setUsername(account.getUsername());
        vo.setStatus(account.getStatus());
        vo.setRole(account.getRole());
        vo.setCreateTime(account.getCreateTime());
        return vo;
    }

    public UserProfileVO getAuthorProfileDetail(Long accountId) {
        com.orangejd.pojo.entity.UserProfile profile = userProfileMapper.selectByAccountId(accountId);
        if (profile == null) return null;
        UserProfileVO vo = new UserProfileVO();
        vo.setId(profile.getId()); vo.setAccountId(profile.getAccountId());
        vo.setNickname(profile.getNickname()); vo.setAvatarUrl(profile.getAvatarUrl());
        vo.setBio(profile.getBio()); vo.setSkills(profile.getSkills());
        vo.setContactIntro(profile.getContactIntro());
        vo.setHomepageVisible(profile.getHomepageVisible());
        vo.setCreateTime(profile.getCreateTime());
        return vo;
    }

    public PageResult<WorkVO> getAuthorWorks(Long accountId, int page, int size) {
        size = com.orangejd.common.util.PageUtil.capSize(size);
        int offset = (page - 1) * size;
        List<Work> works = workMapper.selectByOwnerId(accountId, offset, size);
        Long currentId = BaseContext.getCurrentId();
        boolean isOwner = currentId != null && currentId.equals(accountId);

        // 非本人只展示审核通过且公开的作品
        List<WorkVO> records = works.stream().filter(w -> {
            if (isOwner) return true;
            return "APPROVED".equals(w.getAuditStatus()) && "PUBLIC".equals(w.getVisibilityStatus());
        }).map(w -> {
            WorkVO vo = new WorkVO();
            vo.setId(w.getId()); vo.setOwnerId(w.getOwnerId()); vo.setTitle(w.getTitle());
            vo.setSummary(w.getSummary()); vo.setCategoryId(w.getCategoryId());
            vo.setCoverUrl(w.getCoverUrl()); vo.setAuditStatus(w.getAuditStatus());
            vo.setVisibilityStatus(w.getVisibilityStatus());
            vo.setCreateTime(w.getCreateTime()); vo.setUpdateTime(w.getUpdateTime());
            return vo;
        }).collect(Collectors.toList());

        return new PageResult<>((long) records.size(), records);
    }
}
