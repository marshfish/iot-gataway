package com.hc.business.dto;

import lombok.Data;

/**
 * 所有需要分页查询的需要继承该DTO
 */
@Data
public class PageDTO {
    private int pageNumber = 0;
    private int pageSize = 10;
}
