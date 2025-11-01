/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class User {
    private Long id;
    private String name;
    private String email;
    private String city;
    private LocalDateTime registration_date;
    private Boolean is_active;

    @Override
    public String toString() {
        return String.format(
                "User{id=%d, name='%s', email='%s', city='%s', registration_date=%s, is_active=%s}",
                id, name, email, city, registration_date, is_active);
    }
}
