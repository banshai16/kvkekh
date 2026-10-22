package in.nic.kvkekh.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** One clock in Indian Standard Time, injectable so time-dependent logic can be tested. */
@Configuration
class ClockConfig {

    static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Bean
    Clock clock() {
        return Clock.system(IST);
    }
}
