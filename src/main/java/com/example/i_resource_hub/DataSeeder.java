package com.example.i_resource_hub;

import com.example.i_resource_hub.entity.Role;
import com.example.i_resource_hub.entity.User;
import com.example.i_resource_hub.repository.RoleRepository;
import com.example.i_resource_hub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        // 1. Tạo Roles nếu chưa có
        if (roleRepository.count() == 0) {
            Role adminRole = Role.builder().roleCode("ADMIN").roleName("Quản trị hệ thống").build();
            Role studentRole = Role.builder().roleCode("STUDENT").roleName("Sinh viên").build();
            Role managerRole = Role.builder().roleCode("MANAGER").roleName("Quản lý phòng Lab").build();

            roleRepository.save(adminRole);
            roleRepository.save(studentRole);
            roleRepository.save(managerRole);
            System.out.println("Đã khởi tạo Roles thành công!");
        }

        // 2. Tạo Admin User nếu chưa có
        if (!userRepository.existsByUsername("admin")) {
            User admin = User.builder()
                    .username("admin")
                    .password(passwordEncoder.encode("123456"))
                    .fullName("Super Admin")
                    .email("admin@school.edu.vn")
                    .studentCode("ADMIN001")
                    .status("ACTIVE")
                    .creditScore(100)
                    .build();


            userRepository.save(admin);
            System.out.println("Đã khởi tạo User Admin (Pass: 123456)");
        }
    }
}