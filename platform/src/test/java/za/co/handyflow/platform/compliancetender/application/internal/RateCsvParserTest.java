package za.co.handyflow.platform.compliancetender.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateCsvParserTest {

    @Test
    @DisplayName("amounts are read the way suppliers write them")
    void money() {
        assertThat(RateCsvParser.money("R 1 250,50")).isEqualByComparingTo("1250.50");
        assertThat(RateCsvParser.money("1,250.50")).isEqualByComparingTo("1250.50");
        assertThat(RateCsvParser.money("1.250,50")).isEqualByComparingTo("1250.50");
        assertThat(RateCsvParser.money("R3,400")).isEqualByComparingTo("3400");
        assertThat(RateCsvParser.money("12,5")).isEqualByComparingTo("12.50");
        assertThat(RateCsvParser.money("ZAR 7")).isEqualByComparingTo("7");
    }

    @Test
    @DisplayName("an amount that would have to be rounded, a negative one and a non-number are refused")
    void moneyRefusals() {
        assertThatThrownBy(() -> RateCsvParser.money("12.345")).hasMessageContaining("more than 2 decimal places");
        assertThatThrownBy(() -> RateCsvParser.money("-4")).hasMessageContaining("negative");
        assertThatThrownBy(() -> RateCsvParser.money("(4)")).hasMessageContaining("negative");
        assertThatThrownBy(() -> RateCsvParser.money("abc")).hasMessageContaining("not an amount");
    }

    @Test
    @DisplayName("a semicolon file with a byte-order mark, quoted separators and header synonyms reads")
    void semicolonFile() {
        String csv = "﻿Item;UOM;Unit Price;Type;Vendor\r\n\"Cement 50kg; OPC\";bag;R 1 250,50;Material;BuildIt\r\nSand;m3;300;;\r\n";
        var p = RateCsvParser.parse(csv, null);
        assertThat(p.fatal()).isNull();
        assertThat(p.problems()).isEmpty();
        assertThat(p.rows()).hasSize(2);
        assertThat(p.rows().get(0).description()).isEqualTo("Cement 50kg; OPC");
        assertThat(p.rows().get(0).unit()).isEqualTo("bag");
        assertThat(p.rows().get(0).unitCost()).isEqualByComparingTo(new BigDecimal("1250.50"));
        assertThat(p.rows().get(0).supplier()).isEqualTo("BuildIt");
        assertThat(p.rows().get(0).category()).isEqualTo("MATERIAL");
        assertThat(p.rows().get(1).category()).isNull();     // not given: the caller decides
    }

    @Test
    @DisplayName("a quoted field can hold a line break, and later rows keep their real line numbers")
    void multilineField() {
        var p = RateCsvParser.parse("Description,Rate\n\"Multi\nline\",5\nNext,R 7\n", null);
        assertThat(p.rows()).hasSize(2);
        assertThat(p.rows().get(0).description()).isEqualTo("Multi line");
        assertThat(p.rows().get(1).line()).isEqualTo(4);
    }

    @Test
    @DisplayName("bad rows are reported against their line and skipped; the good ones still read")
    void badRows() {
        String csv = "Description,Unit,Cost,Category\nBad,m,12.345,\nNeg,m,-1,\nOdd,m,5,Wizard\nSand,m3,9,\nSand,m3,10,\n,m,1,\nOk,m,2,labour\n";
        var p = RateCsvParser.parse(csv, null);
        assertThat(p.rows()).extracting(RateCsvParser.Row::description).containsExactly("Sand", "Ok");
        assertThat(p.problems()).extracting(RateCsvParser.Problem::line).containsExactly(2, 3, 4, 6, 7);
        assertThat(p.problems().get(3).message()).contains("listed twice").contains("line 5");
    }

    @Test
    @DisplayName("a file with no header, no description column or no cost column is refused as a whole")
    void fatal() {
        assertThat(RateCsvParser.parse("", null).fatal()).contains("empty");
        assertThat(RateCsvParser.parse("a,b\n1,2", null).fatal()).contains("description");
        assertThat(RateCsvParser.parse("Description,Unit\nx,m", null).fatal()).contains("cost");
        assertThat(RateCsvParser.parse("Description,Cost\nx,1", "WIZARD").fatal()).contains("default category");
    }

    @Test
    @DisplayName("the default category is the one chosen, else Material")
    void defaultCategory() {
        assertThat(RateCsvParser.defaultCategory(null)).isEqualTo("MATERIAL");
        assertThat(RateCsvParser.defaultCategory("  ")).isEqualTo("MATERIAL");
        assertThat(RateCsvParser.defaultCategory("Labour")).isEqualTo("LABOUR");
    }
}
