package com.ansimgil.ansimgil_backend.disaster;

import java.time.LocalDate;

public interface DisasterMessageClient {
    String fetch(int pageNo, int numOfRows, LocalDate startDate);
}
