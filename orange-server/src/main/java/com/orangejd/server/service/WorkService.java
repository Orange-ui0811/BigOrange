package com.orangejd.server.service;

import com.orangejd.pojo.dto.WorkCreateDTO;
import com.orangejd.pojo.dto.WorkEditDTO;
import com.orangejd.pojo.dto.WorkQueryDTO;
import com.orangejd.pojo.vo.WorkDetailVO;
import com.orangejd.pojo.vo.WorkVO;
import com.orangejd.common.result.PageResult;

public interface WorkService {

    WorkDetailVO create(WorkCreateDTO dto);

    WorkDetailVO edit(Long id, WorkEditDTO dto);

    void submitForReview(Long id);

    void delete(Long id);

    void toggleVisibility(Long id);

    void offline(Long id, String reason);

    WorkDetailVO getDetail(Long id);

    PageResult<WorkVO> listMyWorks(int page, int size);

    /** 市场列表 */
    PageResult<WorkVO> listPublic(WorkQueryDTO query);

    /** 管理端列表 */
    PageResult<WorkVO> listAll(WorkQueryDTO query);
}
