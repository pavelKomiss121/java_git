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
    private LocalDateTime createdAt;

    @Override
    public String toString() {
        return String.format(
                "User{id=%d, name='%s', email='%s', createdAt=%s}", id, name, email, createdAt);
    }
}
