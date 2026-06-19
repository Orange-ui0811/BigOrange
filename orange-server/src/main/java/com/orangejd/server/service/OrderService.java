package com.orangejd.server.service;

import com.orangejd.common.result.PageResult;
import com.orangejd.pojo.dto.OrderCreateDTO;
import com.orangejd.pojo.vo.OrderVO;

public interface OrderService {
    OrderVO create(OrderCreateDTO dto);
    OrderVO getDetail(Long id);
    PageResult<OrderVO> listBuy(int page, int size);
    PageResult<OrderVO> listSell(int page, int size);
    void payDeposit(Long id);
    void deliver(Long id, String content, String attachmentUrl);
    void confirmDelivery(Long id);
    void payFinal(Long id);
    void cancel(Long id);
    void delete(Long id);
}
