package com.orangejd.server.service;

import com.orangejd.pojo.dto.RequirementCreateDTO;
import com.orangejd.pojo.dto.RequirementEditDTO;
import com.orangejd.pojo.dto.RequirementQueryDTO;
import com.orangejd.pojo.vo.RequirementVO;
import com.orangejd.common.result.PageResult;

public interface RequirementService {

    RequirementVO create(RequirementCreateDTO dto);

    RequirementVO edit(Long id, RequirementEditDTO dto);

    void submitForReview(Long id);

    void delete(Long id);

    void toggleVisibility(Long id);

    void offline(Long id, String reason);

    RequirementVO getDetail(Long id);

    PageResult<RequirementVO> listMyRequirements(int page, int size);

    PageResult<RequirementVO> listPublic(RequirementQueryDTO query);

    PageResult<RequirementVO> listAll(RequirementQueryDTO query);
}
