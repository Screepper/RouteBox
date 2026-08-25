package com.routebox.entity.attack;

import com.routebox.entity.index.AttackLog;
import com.routebox.manager.attack.AttackHandler;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Description Attack Event
 * @Date 2025/5/8
**/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AttackEvent {
    private String eventId; // Attack eventId
    private Long startTime; // Attack start time
    private Long endTime; // Attack end time
    private AttackLog attackLog; // Attack log
    private AttackHandler attackHandler; // Attack handler
}
