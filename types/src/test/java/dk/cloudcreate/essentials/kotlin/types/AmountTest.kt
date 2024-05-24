package dk.cloudcreate.essentials.kotlin.types

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class AmountTest {
    @Test
    fun `equals`() {
        val amount1 = Amount("100.50")
        val amount2 = Amount("100.50")

        assertThat(amount1).isEqualTo(amount2)
    }

    @Test
    fun `should add amounts correctly`() {
        val amount1 = Amount("100.50")
        val amount2 = Amount("50.25")
        val result = amount1 + amount2

        assertThat(result.value).isEqualByComparingTo(BigDecimal("150.75"))
    }

    @Test
    fun `should subtract amounts correctly`() {
        val amount1 = Amount("100.50")
        val amount2 = Amount("50.25")
        val result = amount1 - amount2

        assertThat(result.value).isEqualByComparingTo(BigDecimal("50.25"))
    }

    @Test
    fun `should multiply amounts correctly`() {
        val amount1 = Amount("100.50")
        val amount2 = Amount("2.00")
        val result = amount1 * amount2

        assertThat(result.value).isEqualByComparingTo(BigDecimal("201.00"))
    }

    @Test
    fun `should divide amounts correctly`() {
        val amount1 = Amount("100.50")
        val amount2 = Amount("2.00")
        val result = amount1 / amount2

        assertThat(result.value).isEqualByComparingTo(BigDecimal("50.25"))
    }

    @Test
    fun `should compare amounts correctly`() {
        val amount1 = Amount("100.50")
        val amount2 = Amount("50.25")

        assertThat(amount1).isGreaterThan(amount2)
        assertThat(amount2).isLessThan(amount1)
    }

    @Test
    fun `should return unary plus correctly`() {
        val amount = Amount("100.50")
        val result = +amount

        assertThat(result.value).isEqualByComparingTo(BigDecimal("100.50"))
    }

    @Test
    fun `should return unary minus correctly`() {
        val amount = Amount("100.50")
        val result = -amount

        assertThat(result.value).isEqualByComparingTo(BigDecimal("-100.50"))
    }

    @Test
    fun `should return absolute value correctly`() {
        val amount = Amount("-100.50")
        val result = amount.abs()

        assertThat(result.value).isEqualByComparingTo(BigDecimal("100.50"))
    }
}