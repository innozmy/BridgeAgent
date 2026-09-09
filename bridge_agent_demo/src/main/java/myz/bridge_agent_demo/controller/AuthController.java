package myz.bridge_agent_demo.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import myz.bridge_agent_demo.common.Result;
import myz.bridge_agent_demo.dto.LoginRequest;
import myz.bridge_agent_demo.dto.PasswordChangeRequest;
import myz.bridge_agent_demo.dto.ProfileUpdateRequest;
import myz.bridge_agent_demo.service.AuthService;
import myz.bridge_agent_demo.vo.AuthVO;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<AuthVO> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    @GetMapping("/me")
    public Result<AuthVO> me() {
        return Result.success(authService.me());
    }

    @PutMapping("/profile")
    public Result<AuthVO> profile(@Valid @RequestBody ProfileUpdateRequest request) {
        return Result.success(authService.updateProfile(request));
    }

    @PutMapping("/password")
    public Result<Void> password(@Valid @RequestBody PasswordChangeRequest request) {
        authService.changePassword(request);
        return Result.success();
    }

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<AuthVO> avatar(@RequestParam("file") MultipartFile file) {
        return Result.success(authService.uploadAvatar(file));
    }

    @GetMapping("/avatars/{userId}")
    public ResponseEntity<Resource> avatarFile(@PathVariable Long userId) {
        return authService.avatar(userId);
    }
}
