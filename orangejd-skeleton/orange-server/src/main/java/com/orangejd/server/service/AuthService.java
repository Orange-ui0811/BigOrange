package com.orangejd.server.service;

import com.orangejd.pojo.dto.LoginDTO;
import com.orangejd.pojo.vo.LoginVO;

public interface AuthService {
    LoginVO adminLogin(LoginDTO loginDTO);
}

