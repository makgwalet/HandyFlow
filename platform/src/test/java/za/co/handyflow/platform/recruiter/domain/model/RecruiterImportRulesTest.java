package za.co.handyflow.platform.recruiter.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecruiterImportRulesTest {

    @Test
    void readsAnyColumnOrderSemicolonsQuotesAndABom() {
        var p = RecruiterImportRules.parse("﻿First Name;Surname;E-mail;Mobile\nThabo;Mokoena;THABO@x.co.za;082 123 4567\n\"Ann\";\"O\"\"Neil\";ann@x.co;\n");
        assertThat(p.fileProblem()).isNull();
        assertThat(p.rows()).hasSize(2);
        assertThat(p.rows().get(0).email()).isEqualTo("thabo@x.co.za");
        assertThat(p.rows().get(1).lastName()).isEqualTo("O\"Neil");
        assertThat(p.rows().get(0).source()).isEqualTo("IMPORT");
    }

    @Test
    void aSingleNameColumnIsSplitOnTheLastSpace() {
        var p = RecruiterImportRules.parse("Name,Email\nJohn van der Merwe,j@x.co\n");
        assertThat(p.rows().get(0).firstName()).isEqualTo("John van der");
        assertThat(p.rows().get(0).lastName()).isEqualTo("Merwe");
    }

    @Test
    void badLinesAreReportedAndDuplicatesInTheFileAreRefused() {
        var p = RecruiterImportRules.parse("first,last,email\nA,B,a@x.co\nC,D,nope\nE,F,A@x.co\n");
        assertThat(p.rows()).hasSize(1);
        assertThat(p.badLines()).containsKeys(3, 4);
    }

    @Test
    void aLeadingFormulaCharacterIsDropped() {
        var p = RecruiterImportRules.parse("first,last,email\n=cmd,X,x@y.co\n");
        assertThat(p.rows().get(0).firstName()).isEqualTo("cmd");
    }

    @Test
    void theFileMustHaveTheRightColumnsAndSize() {
        assertThat(RecruiterImportRules.parse("").fileProblem()).isNotNull();
        assertThat(RecruiterImportRules.parse("a,b\n1,2").fileProblem()).contains("email");
        assertThat(RecruiterImportRules.parse("email\nx@y.co").fileProblem()).contains("first name");
        StringBuilder big = new StringBuilder("first,last,email\n");
        for (int i = 0; i <= RecruiterImportRules.MAX_ROWS; i++) big.append("A,B,a").append(i).append("@x.co\n");
        assertThat(RecruiterImportRules.parse(big.toString()).fileProblem()).contains("Too many");
    }

    @Test
    void importNeedsTheLawfulBasisConfirmed() {
        assertThat(RecruiterImportRules.basisProblem(null)).isNotNull();
        assertThat(RecruiterImportRules.basisProblem(false)).isNotNull();
        assertThat(RecruiterImportRules.basisProblem(true)).isNull();
    }
}
