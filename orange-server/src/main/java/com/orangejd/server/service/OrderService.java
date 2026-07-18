package com.orangejd.server.service;

import com.orangejd.common.result.PageResult;
import com.orangejd.pojo.dto.OrderCreateDTO;
import com.orangejd.pojo.dto.DeliverySubmitDTO;
import com.orangejd.pojo.vo.DeliveryAttachmentVO;
import com.orangejd.pojo.vo.OrderVO;
import org.springframework.web.multipart.MultipartFile;

public interface OrderService {
    OrderVO create(OrderCreateDTO dto);
    OrderVO getDetail(Long id);
    PageResult<OrderVO> listBuy(int page, int size);
    PageResult<OrderVO> listSell(int page, int size);
    void payDeposit(Long id);
    DeliveryAttachmentVO uploadDeliveryAttachment(Long id, MultipartFile file);
    void deletePendingDeliveryAttachment(Long id, String objectKey);
    String getDeliveryAttachmentUrl(Long orderId, Long deliveryId);
    void deliver(Long id, DeliverySubmitDTO dto);
    void confirmDelivery(Long id);
    void payFinal(Long id);
    void cancel(Long id);
    void delete(Long id);
}
