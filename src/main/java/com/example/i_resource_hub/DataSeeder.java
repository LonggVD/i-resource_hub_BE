package com.example.i_resource_hub;

import com.example.i_resource_hub.entity.*;
import com.example.i_resource_hub.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OrganizationUnitRepository unitRepository;
    private final PermissionRepository permissionRepository;

    // Bổ sung các Repo để tạo dữ liệu mượn trả
    private final TimeSlotRepository timeSlotRepository;
    private final ResourceItemRepository resourceItemRepository;
    private final BookingRepository bookingRepository;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        System.out.println("🔄 Đang kiểm tra và đồng bộ dữ liệu hệ thống...");

        // ==========================================
        // 1. KHỞI TẠO PERMISSION & ROLE (Giữ nguyên của bác)
        // ==========================================
        ensurePermission("USER_MANAGE", "USER", "MANAGE", "Quản lý người dùng");
        ensurePermission("RESOURCE_MANAGE", "RESOURCE", "MANAGE", "Quản lý tài nguyên");
        ensurePermission("RESOURCE_VIEW", "RESOURCE", "VIEW", "Xem tài nguyên");
        ensurePermission("BOOKING_MANAGE", "BOOKING", "MANAGE", "Quản lý yêu cầu mượn");
        ensurePermission("BOOKING_APPROVE", "BOOKING", "APPROVE", "Duyệt yêu cầu mượn"); // Thêm quyền Duyệt
        ensurePermission("BOOKING_CREATE", "BOOKING", "CREATE", "Gửi yêu cầu mượn");
        ensurePermission("CATEGORY_MANAGE", "CATEGORY", "MANAGE", "Quản lý danh mục");
        ensurePermission("UNIT_MANAGE", "UNIT", "MANAGE", "Quản lý đơn vị");
        ensurePermission("INVENTORY_CHECKIN", "INVENTORY", "CHECKIN", "Thực hiện bàn giao thiết bị");
        ensurePermission("INVENTORY_CHECKOUT", "INVENTORY", "CHECKOUT", "Thực hiện nhận lại thiết bị");

        Role adminRole = ensureRole("ADMIN", "Quản trị hệ thống", true);
        Role studentRole = ensureRole("STUDENT", "Sinh viên", false);
        Role managerRole = ensureRole("MANAGER", "Quản lý phòng Lab", false);

        // Cập nhật quyền cho ADMIN
        List<Permission> allPerms = permissionRepository.findAll();
        adminRole.setPermissions(new HashSet<>(allPerms));
        roleRepository.save(adminRole);

        // Cập nhật quyền cho STUDENT
        HashSet<Permission> studentPerms = new HashSet<>();
        permissionRepository.findByPermissionCode("RESOURCE_VIEW").ifPresent(studentPerms::add);
        permissionRepository.findByPermissionCode("BOOKING_CREATE").ifPresent(studentPerms::add);
        studentRole.setPermissions(studentPerms);
        roleRepository.save(studentRole);

        // Cập nhật quyền cho MANAGER (Merge vào tập hiện tại để Hibernate phát hiện thay đổi đúng)
        Set<Permission> managerPerms = managerRole.getPermissions() != null
                ? new HashSet<>(managerRole.getPermissions())
                : new HashSet<>();
        permissionRepository.findByPermissionCode("RESOURCE_VIEW").ifPresent(managerPerms::add);
        permissionRepository.findByPermissionCode("RESOURCE_MANAGE").ifPresent(managerPerms::add);
        permissionRepository.findByPermissionCode("BOOKING_MANAGE").ifPresent(managerPerms::add);
        permissionRepository.findByPermissionCode("BOOKING_APPROVE").ifPresent(managerPerms::add);
        permissionRepository.findByPermissionCode("INVENTORY_CHECKIN").ifPresent(managerPerms::add);
        permissionRepository.findByPermissionCode("INVENTORY_CHECKOUT").ifPresent(managerPerms::add);
        managerRole.setPermissions(managerPerms);
        roleRepository.save(managerRole);

        // ==========================================
        // 2. KHỞI TẠO ĐƠN VỊ (2 Khoa để test bảo mật)
        // ==========================================
        OrganizationUnit cntt = ensureUnit("Khoa Công nghệ thông tin", "FACULTY");
        OrganizationUnit kinhTe = ensureUnit("Khoa Kinh tế", "FACULTY");

        // ==========================================
        // 3. KHỞI TẠO NGƯỜI DÙNG (Admin, Giáo vụ, Sinh viên)
        // ==========================================
        // 3.1. Super Admin
        ensureAdmin(adminRole, cntt);

        // 3.2. Giáo vụ 2 Khoa
        User managerCntt = ensureUser("admin_cntt", "Giáo vụ CNTT", "cntt@abc.com", "MNG001", managerRole, cntt);
        User managerKt = ensureUser("admin_kt", "Giáo vụ Kinh Tế", "kt@abc.com", "MNG002", managerRole, kinhTe);

        // 3.3. Sinh viên
        User sinhVien = ensureUser("sv01", "Vũ Đức Long", "longvd@abc.com", "SV001", studentRole, cntt);

        // ==========================================
        // 4. KHỞI TẠO DỮ LIỆU MƯỢN TRẢ (Slot, Thiết bị, Booking)
        // ==========================================
        if (timeSlotRepository.count() == 0) {

            // ── 4.1. Seed TimeSlot: 5 ca × áp dụng tất cả ngày (dayOfWeek = null) ──
            //
            //  Cách hoạt động của TimeSlot trong hệ thống:
            //  ┌────────────────────────────────────────────────────────────┐
            //  │ 1. Admin seed sẵn các ca (slot) ở đây.                    │
            //  │ 2. Sinh viên gọi GET /api/time-slots → chọn slot_id.      │
            //  │ 3. Khi tạo Booking, sinh viên gửi {slotId, bookingDate,   │
            //  │    resourceTemplateId, purpose}.                           │
            //  │ 4. Booking lưu FK slot_id → time_slots.id.                │
            //  │ 5. Kanban của Manager hiển thị slotName, startTime,       │
            //  │    endTime trên mỗi thẻ đơn mượn.                        │
            //  └────────────────────────────────────────────────────────────┘
            TimeSlot ca1 = timeSlotRepository.save(TimeSlot.builder()
                    .slotName("Ca 1 (07:00 – 09:15)")
                    .startTime(LocalTime.of(7, 0)).endTime(LocalTime.of(9, 15))
                    .build());

            TimeSlot ca2 = timeSlotRepository.save(TimeSlot.builder()
                    .slotName("Ca 2 (09:30 – 11:45)")
                    .startTime(LocalTime.of(9, 30)).endTime(LocalTime.of(11, 45))
                    .build());

            TimeSlot ca3 = timeSlotRepository.save(TimeSlot.builder()
                    .slotName("Ca 3 (13:00 – 15:15)")
                    .startTime(LocalTime.of(13, 0)).endTime(LocalTime.of(15, 15))
                    .build());

            TimeSlot ca4 = timeSlotRepository.save(TimeSlot.builder()
                    .slotName("Ca 4 (15:30 – 17:45)")
                    .startTime(LocalTime.of(15, 30)).endTime(LocalTime.of(17, 45))
                    .build());

            TimeSlot ca5 = timeSlotRepository.save(TimeSlot.builder()
                    .slotName("Ca 5 (18:00 – 20:15)")
                    .startTime(LocalTime.of(18, 0)).endTime(LocalTime.of(20, 15))
                    .build());

            TimeSlot ca6 = timeSlotRepository.save(TimeSlot.builder()
                    .slotName("Ca 6 (20:30 – 22:45)")
                    .startTime(LocalTime.of(20, 30)).endTime(LocalTime.of(22, 45))
                    .build());

            // ── 4.2. Seed ResourceItem ──
            ResourceItem mayChieuCntt = ResourceItem.builder()
                    .serialNumber("SN-MAYCHIEU-001")
                    .status("AVAILABLE")
                    .managedByUnit(cntt)
                    .build();

            ResourceItem loaKeoKt = ResourceItem.builder()
                    .serialNumber("SN-LOAKEO-002")
                    .status("AVAILABLE")
                    .managedByUnit(kinhTe)
                    .build();

            resourceItemRepository.saveAll(List.of(mayChieuCntt, loaKeoKt));

            // ── 4.3. Seed Booking mẫu – mỗi ca một đơn để test Kanban ──
            Booking bookingCa1 = Booking.builder()
                    .user(sinhVien).resourceItem(mayChieuCntt).slot(ca1)
                    .bookingDate(LocalDate.now()).status("PENDING")
                    .purpose("Thuyết trình Lập trình Web").qrCodeToken(UUID.randomUUID().toString()).build();

            Booking bookingCa2 = Booking.builder()
                    .user(sinhVien).resourceItem(mayChieuCntt).slot(ca2)
                    .bookingDate(LocalDate.now().plusDays(1)).status("PENDING")
                    .purpose("Bảo vệ đồ án CNTT").qrCodeToken(UUID.randomUUID().toString()).build();

            Booking bookingKinhTe = Booking.builder()
                    .user(sinhVien).resourceItem(loaKeoKt).slot(ca3)
                    .bookingDate(LocalDate.now()).status("PENDING")
                    .purpose("Sự kiện giao lưu CLB").qrCodeToken(UUID.randomUUID().toString()).build();

            bookingRepository.saveAll(List.of(bookingCa1, bookingCa2, bookingKinhTe));
            System.out.println("✅ Đã seed 5 TimeSlot + 3 Booking mẫu để test Kanban!");
        }


        System.out.println("🚀 Khởi động Database hoàn tất!");
    }

    // ================== CÁC HÀM TIỆN ÍCH (HELPER METHODS) ================== //

    private void ensurePermission(String code, String resource, String action, String desc) {
        if (permissionRepository.findByPermissionCode(code).isEmpty()) {
            Permission p = Permission.builder()
                    .permissionCode(code).resourceCode(resource).actionCode(action).description(desc).build();
            permissionRepository.save(p);
        }
    }

    private Role ensureRole(String code, String name, boolean isSystem) {
        return roleRepository.findByRoleCode(code).orElseGet(() -> {
            Role r = Role.builder().roleCode(code).roleName(name).isSystem(isSystem).status("ACTIVE").build();
            return roleRepository.save(r);
        });
    }

    private OrganizationUnit ensureUnit(String name, String type) {
        // Chú ý: Cần viết thêm hàm findByUnitName trong OrganizationUnitRepository nếu chưa có
        // Tạm thời em dùng logic tìm list để tránh lỗi nếu repo chưa định nghĩa hàm find
        List<OrganizationUnit> units = unitRepository.findAll();
        return units.stream().filter(u -> u.getUnitName().equals(name)).findFirst().orElseGet(() -> {
            OrganizationUnit unit = new OrganizationUnit();
            unit.setUnitName(name);
            unit.setUnitType(type);
            return unitRepository.save(unit);
        });
    }

    private User ensureUser(String username, String fullName, String email, String code, Role role, OrganizationUnit unit) {
        return userRepository.findByUsername(username).orElseGet(() -> {
            User u = User.builder()
                    .username(username)
                    .password(passwordEncoder.encode("123456"))
                    .fullName(fullName)
                    .email(email)
                    .studentCode(code)
                    .status("ACTIVE")
                    .creditScore(100)
                    .roles(Set.of(role))
                    .unit(unit)
                    .build();
            return userRepository.save(u);
        });
    }

    private void ensureAdmin(Role adminRole, OrganizationUnit unit) {
        User admin = userRepository.findByUsername("admin").orElse(null);
        if (admin == null) {
            admin = User.builder()
                    .username("admin").password(passwordEncoder.encode("123456")).fullName("Super Admin")
                    .email("admin@school.edu.vn").studentCode("ADMIN001").status("ACTIVE").creditScore(100)
                    .roles(Set.of(adminRole)).unit(unit).build();
            userRepository.save(admin);
        } else {
            if (admin.getRoles() == null || admin.getRoles().stream().noneMatch(r -> r.getRoleCode().equals("ADMIN"))) {
                Set<Role> roles = admin.getRoles() != null ? new HashSet<>(admin.getRoles()) : new HashSet<>();
                roles.add(adminRole);
                admin.setRoles(roles);
                userRepository.save(admin);
            }
        }
    }
}