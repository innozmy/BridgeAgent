package myz.bridge_agent_demo.service;

import myz.bridge_agent_demo.dto.LoginRequest;
import myz.bridge_agent_demo.dto.PasswordChangeRequest;
import myz.bridge_agent_demo.dto.ProfileUpdateRequest;
import myz.bridge_agent_demo.vo.AuthVO;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

public interface AuthService {

    AuthVO login(LoginRequest request);

    AuthVO me();

    AuthVO updateProfile(ProfileUpdateRequest request);

    void changePassword(PasswordChangeRequest request);

    AuthVO uploadAvatar(MultipartFile file);

    ResponseEntity<Resource> avatar(Long userId);

    /** 管理员改他人密码，立刻废旧票 */
    void adminResetPassword(Long userId, String rawPassword);
}
