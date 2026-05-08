# Báo cáo quét Backend

Đường dẫn quét: `i-resource_hub/i-resource_hub`

## Tóm tắt dự án

- Hệ thống build: Maven (có wrapper `mvnw`, `mvnw.cmd`)
- Spring Boot (parent): 3.3.5
- Mục tiêu Java: 21

## Các thư viện chính (theo `pom.xml`)

- Spring Web, Spring Data JPA, Spring Security
- Validation, Mail, WebSocket
- MySQL connector, Lombok
- OpenAPI/Swagger (springdoc)
- JJWT (thư viện JWT)

## Cấu hình chính (theo `src/main/resources/application.properties`)

- Tên ứng dụng: `i-resource_hub`
- Database: `jdbc:mysql://localhost:3306/i-resource_hub` (user: `root`, mật khẩu được ẩn/không khuyến nghị lưu plaintext)
- JPA: `hibernate.ddl-auto=update` (cập nhật schema tự động)
- Cổng server: `2811`
- Thư mục upload: `uploads` (giới hạn file tối đa 10MB)
- Mail: SMTP `smtp.gmail.com:587` (tên đăng nhập được cấu hình trong file)

## Các tệp nguồn đáng chú ý / cấu trúc

- Lớp chính (entry): `IResourceHubApplication.java`
- Data seeder khởi tạo dữ liệu mẫu: `DataSeeder.java`
- Cấu hình: `SecurityConfig.java`, `OpenApiConfig.java`, `WebMvcConfig.java`
- Bảo mật / JWT: `JWTAuthFilter.java`, `AuthEntryPointJWT.java`, `JWTUtils.java`, `UserDetailsServiceImpl.java`

### Controller (đã triển khai)

- `UserController`, `AuthController`, `FileUploadController`, `CategoryController`, `CartController`, `BookingController`, `ResourceTemplateController`, `ResourceItemController`, `OrganizationUnitController`, `RoleController`, `TimeSlotController`, `TestController`

### Service

- `UserService`, `AuthService`, `BookingService`, `CartService`, `CategoryService`, `ResourceItemService`, `ResourceTemplateService`, `OrganizationUnitService`, `TimeSlotService`, `RoleService`, `EmailService`

### Repository

- `UserRepository`, `CategoryRepository`, `CartItemRepository`, `BookingRepository`, `BookingHistoryRepository`, `BookingEvidenceRepository`, `ResourceTemplateRepository`, `ResourceItemRepository`, `PermissionRepository`, `OrganizationUnitRepository`, `TimeSlotRepository`, `RoleRepository`

### Entity và DTO

- Các entity chính: `User`, `Role`, `Permission`, `OrganizationUnit`, `ResourceItem`, `ResourceTemplate`, `Booking`, `CartItem`, `TimeSlot`, `Notification`, `AuditLog`, `BookingHistory`, `BookingEvidence`, `Penalty`, `ResourceMaintenance`, `SystemConfig`, cùng các base entity.
- DTOs cho request/response đã có: đăng nhập, đăng ký, booking, resource item/template create/update, user request, role detail, JWT response, v.v.

## Tệp và vật chứng khác

- Thư mục `uploads/` dùng lưu file upload
- Thư mục `target/` chứa lớp đã biên dịch (có build trước đó)
- `HELP.md` có ghi chú dự án
- Có các log crash JVM (`hs_err_pid*.log`) trong repo

## Những phần đã sẵn sàng / đã triển khai

- Các endpoint REST cho user/auth, resource items/templates, bookings, categories, cart, organization units, roles, time slots.
- Cấu hình bảo mật bằng JWT và Spring Security.
- Hỗ trợ gửi mail (cấu hình có trong `application.properties`).
- Hỗ trợ OpenAPI/Swagger (cấu hình `OpenApiConfig`).
- Xử lý upload file (`FileUploadController`, `file.upload-dir=uploads`).
- Data seeding để khởi tạo dữ liệu mẫu.

## Kiểm tra/gợi ý tiếp theo (thao tác thủ công)

- Kiểm tra cơ sở dữ liệu MySQL `i-resource_hub` đã tồn tại và thông tin đăng nhập khớp.
- Kiểm tra bảo mật: không lưu mật khẩu/secret (ví dụ mail password, `jwt.secret`) trong mã nguồn công khai.
- Khởi động ứng dụng để kiểm chứng: dùng Maven wrapper trong thư mục dự án:

```bash
./mvnw -U spring-boot:run
```

Hoặc trên Windows (PowerShell / cmd):

```powershell
mvnw.cmd -U spring-boot:run
```

Generated on: 2026-05-02
