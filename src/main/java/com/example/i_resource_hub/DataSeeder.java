package com.example.i_resource_hub;

import com.example.i_resource_hub.entity.OrganizationUnit;
import com.example.i_resource_hub.entity.Permission;
import com.example.i_resource_hub.entity.Role;
import com.example.i_resource_hub.entity.User;
import com.example.i_resource_hub.repository.OrganizationUnitRepository;
import com.example.i_resource_hub.repository.PermissionRepository;
import com.example.i_resource_hub.repository.RoleRepository;
import com.example.i_resource_hub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OrganizationUnitRepository unitRepository;
    private final PermissionRepository permissionRepository;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        // 1. Đảm bảo các Permissions cơ bản tồn tại
        ensurePermission("USER_MANAGE", "USER", "MANAGE", "Quản lý người dùng");
        ensurePermission("RESOURCE_MANAGE", "RESOURCE", "MANAGE", "Quản lý tài nguyên");
        ensurePermission("RESOURCE_VIEW", "RESOURCE", "VIEW", "Xem tài nguyên");
        ensurePermission("BOOKING_MANAGE", "BOOKING", "MANAGE", "Quản lý yêu cầu mượn");
        ensurePermission("BOOKING_REQUEST", "BOOKING", "REQUEST", "Gửi yêu cầu mượn");
        ensurePermission("CATEGORY_MANAGE", "CATEGORY", "MANAGE", "Quản lý danh mục");
        ensurePermission("UNIT_MANAGE", "UNIT", "MANAGE", "Quản lý đơn vị");

        // 2. Đảm bảo các Roles cơ bản tồn tại
        Role adminRole = ensureRole("ADMIN", "Quản trị hệ thống", true);
        Role studentRole = ensureRole("STUDENT", "Sinh viên", false);
        Role managerRole = ensureRole("MANAGER", "Quản lý phòng Lab", false);

        // 3. Cập nhật quyền cho Roles (Gắn lại quyền để chắc chắn không bị trống)
        List<Permission> allPerms = permissionRepository.findAll();
        adminRole.setPermissions(new HashSet<>(allPerms));
        roleRepository.save(adminRole);

        HashSet<Permission> studentPerms = new HashSet<>();
        permissionRepository.findByPermissionCode("RESOURCE_VIEW").ifPresent(studentPerms::add);
        permissionRepository.findByPermissionCode("BOOKING_REQUEST").ifPresent(studentPerms::add);
        studentRole.setPermissions(studentPerms);
        roleRepository.save(studentRole);

        HashSet<Permission> managerPerms = new HashSet<>();
        permissionRepository.findByPermissionCode("RESOURCE_VIEW").ifPresent(managerPerms::add);
        permissionRepository.findByPermissionCode("RESOURCE_MANAGE").ifPresent(managerPerms::add);
        permissionRepository.findByPermissionCode("BOOKING_MANAGE").ifPresent(managerPerms::add);
        managerRole.setPermissions(managerPerms);
        roleRepository.save(managerRole);

        System.out.println("Đã đồng bộ hóa Roles và Permissions!");

        // 4. Đảm bảo Đơn vị mẫu
        if (unitRepository.count() == 0) {
            OrganizationUnit cntt = new OrganizationUnit();
            cntt.setUnitName("Khoa Công nghệ thông tin");
            cntt.setUnitType("FACULTY");
            unitRepository.save(cntt);
            System.out.println("Đã tạo đơn vị mẫu.");
        }

        // 5. Đảm bảo tài khoản admin tồn tại và có quyền ADMIN
        User admin = userRepository.findByUsername("admin").orElse(null);
        if (admin == null) {
            admin = User.builder()
                    .username("admin")
                    .password(passwordEncoder.encode("123456"))
                    .fullName("Super Admin")
                    .email("admin@school.edu.vn")
                    .studentCode("ADMIN001")
                    .status("ACTIVE")
                    .creditScore(100)
                    .roles(Set.of(adminRole))
                    .build();
            userRepository.save(admin);
            System.out.println("Đã tạo tài khoản admin mới (pass: 123456)");
        } else {
            // Nếu admin đã tồn tại, đảm bảo nó có role ADMIN
            if (admin.getRoles() == null || admin.getRoles().stream().noneMatch(r -> r.getRoleCode().equals("ADMIN"))) {
                Set<Role> roles = admin.getRoles() != null ? new HashSet<>(admin.getRoles()) : new HashSet<>();
                roles.add(adminRole);
                admin.setRoles(roles);
                userRepository.save(admin);
                System.out.println("Đã cập nhật quyền ADMIN cho tài khoản admin hiện tại.");
            }
        }
    }

    private void ensurePermission(String code, String resource, String action, String desc) {
        if (permissionRepository.findByPermissionCode(code).isEmpty()) {
            Permission p = Permission.builder()
                    .permissionCode(code)
                    .resourceCode(resource)
                    .actionCode(action)
                    .description(desc)
                    .build();
            permissionRepository.save(p);
        }
    }

    private Role ensureRole(String code, String name, boolean isSystem) {
        return roleRepository.findByRoleCode(code).orElseGet(() -> {
            Role r = Role.builder()
                    .roleCode(code)
                    .roleName(name)
                    .isSystem(isSystem)
                    .status("ACTIVE")
                    .build();
            return roleRepository.save(r);
        });
    }
}