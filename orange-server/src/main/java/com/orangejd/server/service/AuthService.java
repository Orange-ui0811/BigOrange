package com.orangejd.server.service;

import com.orangejd.pojo.dto.LoginDTO;
import com.orangejd.pojo.dto.PasswordChangeDTO;
import com.orangejd.pojo.dto.RegisterDTO;
import com.orangejd.pojo.vo.LoginVO;

public interface AuthService {
    LoginVO adminLogin(LoginDTO loginDTO);

    LoginVO appLogin(LoginDTO loginDTO);

    LoginVO register(RegisterDTO registerDTO);

    void changePassword(PasswordChangeDTO passwordChangeDTO);
}

