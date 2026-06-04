/* Helper (componente de ayuda) para calcular horas laborales entre dos fechas.
   Usa la librería Jollyday para obtener los festivos nacionales y autonómicos de España
   (con la configuración de Baleares) y añade los festivos locales de Palma (Sant Sebastià y Festa de l'Estendard).
   Excluye sábados, domingos y festivos del cómputo de días laborables. */
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

    /* Gestor de festivos configurado para el calendario de España. */
    private final HolidayManager manager = HolidayManager
            .getInstance(ManagerParameters.create(HolidayCalendar.SPAIN));

    /* Festivos locales de Palma de Mallorca: Sant Sebastià (20 enero) y Festa de l'Estendard (31 diciembre). */
    private static final List<MonthDay> FESTIVOS_PALMA = List.of(
            MonthDay.of(1, 20),
            MonthDay.of(12, 31)
    );

    /* Calcula las horas laborales entre dos fechas (inclusive).
       Recibe la fecha de inicio, la fecha de fin y las horas por día.
       Cuenta los días que no son sábado, domingo, festivo nacional (Baleares) ni festivo local de Palma,
       y los multiplica por las horas/día. */
    public double calcularHorasLaborales(
            LocalDate inicio, LocalDate fin, double horasDia) {

        return inicio.datesUntil(fin.plusDays(1))
                .filter(d -> d.getDayOfWeek() != DayOfWeek.SATURDAY
                        && d.getDayOfWeek() != DayOfWeek.SUNDAY
                        && !manager.isHoliday(d, "ib")
                        && !esFestivoLocalPalma(d))
                .count() * horasDia;
    }

    /* Comprueba si una fecha es uno de los festivos locales de Palma. */
    private boolean esFestivoLocalPalma(LocalDate fecha) {
        return FESTIVOS_PALMA.contains(MonthDay.from(fecha));
    }
}
