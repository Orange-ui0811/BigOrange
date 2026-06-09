package com.orangejd.server.service;

import com.orangejd.common.result.PageResult;
import com.orangejd.pojo.vo.AccountVO;
import com.orangejd.pojo.vo.UserProfileVO;
import com.orangejd.pojo.vo.WorkVO;

public interface IProfileService {
    AccountVO getAuthorProfile(Long accountId);
    UserProfileVO getAuthorProfileDetail(Long accountId);
    PageResult<WorkVO> getAuthorWorks(Long accountId, int page, int size);
}
