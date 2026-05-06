package com.example.gestion_partes.helper;

import de.focus_shift.jollyday.core.HolidayCalendar;
import de.focus_shift.jollyday.core.HolidayManager;
import de.focus_shift.jollyday.core.ManagerParameters;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.List;

@Component
public class calendario_laboral_helper {

    private final HolidayManager manager = HolidayManager
            .getInstance(ManagerParameters.create(HolidayCalendar.SPAIN));

    // Festivos locales de Palma — día/mes fijos cada año
    private static final List<MonthDay> FESTIVOS_PALMA = List.of(
            MonthDay.of(1, 20),  // Sant Sebastià
            MonthDay.of(12, 31)  // Festa de l'Estendard
    );

    public double calcularHorasLaborales(
            LocalDate inicio, LocalDate fin, double horasDia) {

        return inicio.datesUntil(fin.plusDays(1))
                .filter(d -> d.getDayOfWeek() != DayOfWeek.SATURDAY
                        && d.getDayOfWeek() != DayOfWeek.SUNDAY
                        && !manager.isHoliday(d, "ib") // nacionales + Baleares
                        && !esFestivoLocalPalma(d))
                .count() * horasDia;
    }

    private boolean esFestivoLocalPalma(LocalDate fecha) {
        return FESTIVOS_PALMA.contains(MonthDay.from(fecha));
    }
}