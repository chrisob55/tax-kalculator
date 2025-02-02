/*
 * Copyright 2019 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.gov.hmrc.calculator

import uk.gov.hmrc.calculator.model.BandBreakdown
import uk.gov.hmrc.calculator.model.CalculatorResponse
import uk.gov.hmrc.calculator.model.CalculatorResponsePayPeriod
import uk.gov.hmrc.calculator.model.Country
import uk.gov.hmrc.calculator.model.Country.ENGLAND
import uk.gov.hmrc.calculator.model.PayPeriod
import uk.gov.hmrc.calculator.model.PayPeriod.FOUR_WEEKLY
import uk.gov.hmrc.calculator.model.PayPeriod.MONTHLY
import uk.gov.hmrc.calculator.model.PayPeriod.WEEKLY
import uk.gov.hmrc.calculator.model.PayPeriod.YEARLY
import uk.gov.hmrc.calculator.model.bands.Band
import uk.gov.hmrc.calculator.model.bands.EmployeeNIBands
import uk.gov.hmrc.calculator.model.bands.EmployerNIBands
import uk.gov.hmrc.calculator.model.bands.TaxBand
import uk.gov.hmrc.calculator.model.bands.TaxBands
import uk.gov.hmrc.calculator.model.taxcodes.AdjustedTaxFreeTCode
import uk.gov.hmrc.calculator.model.taxcodes.EmergencyTaxCode
import uk.gov.hmrc.calculator.model.taxcodes.KTaxCode
import uk.gov.hmrc.calculator.model.taxcodes.MarriageTaxCodes
import uk.gov.hmrc.calculator.model.taxcodes.NoTaxTaxCode
import uk.gov.hmrc.calculator.model.taxcodes.SingleBandTax
import uk.gov.hmrc.calculator.model.taxcodes.StandardTaxCode
import uk.gov.hmrc.calculator.exception.InvalidTaxBandException
import uk.gov.hmrc.calculator.exception.InvalidTaxCodeException
import uk.gov.hmrc.calculator.exception.InvalidWagesException
import uk.gov.hmrc.calculator.model.taxcodes.TaxCode
import uk.gov.hmrc.calculator.utils.TaxYear
import uk.gov.hmrc.calculator.utils.convertAmountFromYearlyToPayPeriod
import uk.gov.hmrc.calculator.utils.convertListOfBandBreakdownForPayPeriod
import uk.gov.hmrc.calculator.utils.convertWageToYearly
import uk.gov.hmrc.calculator.utils.toTaxCode

class Calculator(
    private val taxCode: String,
    private val wages: Double,
    private val payPeriod: PayPeriod,
    private val pensionAge: Boolean = false,
    private val hoursPerWeek: Double? = null,
    private val taxYear: Int = TaxYear().currentTaxYear()
) {

    private val bandBreakdown: MutableList<BandBreakdown> = mutableListOf()

    fun run(): CalculatorResponse {
        if (!isAboveMinimumWages(wages) || !isBelowMaximumWages(wages)) {
            throw InvalidWagesException("Wages must be between 0 and 9999999.99")
        }

        val yearlyWages = wages.convertWageToYearly(payPeriod, hoursPerWeek)

        val taxCode = this.taxCode.toTaxCode()

        val taxBands = TaxBands(taxCode.country, taxYear).bands

        val taxPayable = taxToPay(yearlyWages, taxCode)
        val employeesNI = employeeNIToPay(yearlyWages)
        val employersNI = employerNIToPay(yearlyWages)

        val taxFreeAmount = adjustTaxBands(taxBands, taxCode)[0].upper
        val amountToAddToWages = if (taxCode is KTaxCode) taxCode.amountToAddToWages else null

        return CalculatorResponse(
            taxCode = this.taxCode,
            country = taxCode.country,
            isKCode = taxCode is KTaxCode,
            weekly = CalculatorResponsePayPeriod(
                payPeriod = WEEKLY,
                taxToPay = taxPayable.convertAmountFromYearlyToPayPeriod(WEEKLY),
                employeesNI = employeesNI.convertAmountFromYearlyToPayPeriod(WEEKLY),
                employersNI = employersNI.convertAmountFromYearlyToPayPeriod(WEEKLY),
                wages = yearlyWages.convertAmountFromYearlyToPayPeriod(WEEKLY),
                taxBreakdown = bandBreakdown.convertListOfBandBreakdownForPayPeriod(WEEKLY),
                taxFree = taxFreeAmount.convertAmountFromYearlyToPayPeriod(WEEKLY),
                kCodeAdjustment = amountToAddToWages?.convertAmountFromYearlyToPayPeriod(WEEKLY)
            ),
            fourWeekly = CalculatorResponsePayPeriod(
                payPeriod = FOUR_WEEKLY,
                taxToPay = taxPayable.convertAmountFromYearlyToPayPeriod(FOUR_WEEKLY),
                employeesNI = employeesNI.convertAmountFromYearlyToPayPeriod(FOUR_WEEKLY),
                employersNI = employersNI.convertAmountFromYearlyToPayPeriod(FOUR_WEEKLY),
                wages = yearlyWages.convertAmountFromYearlyToPayPeriod(FOUR_WEEKLY),
                taxBreakdown = bandBreakdown.convertListOfBandBreakdownForPayPeriod(FOUR_WEEKLY),
                taxFree = taxFreeAmount.convertAmountFromYearlyToPayPeriod(FOUR_WEEKLY),
                kCodeAdjustment = amountToAddToWages?.convertAmountFromYearlyToPayPeriod(FOUR_WEEKLY)
            ),
            monthly = CalculatorResponsePayPeriod(
                payPeriod = MONTHLY,
                taxToPay = taxPayable.convertAmountFromYearlyToPayPeriod(MONTHLY),
                employeesNI = employeesNI.convertAmountFromYearlyToPayPeriod(MONTHLY),
                employersNI = employersNI.convertAmountFromYearlyToPayPeriod(MONTHLY),
                wages = yearlyWages.convertAmountFromYearlyToPayPeriod(MONTHLY),
                taxBreakdown = bandBreakdown.convertListOfBandBreakdownForPayPeriod(MONTHLY),
                taxFree = taxFreeAmount.convertAmountFromYearlyToPayPeriod(MONTHLY),
                kCodeAdjustment = amountToAddToWages?.convertAmountFromYearlyToPayPeriod(MONTHLY)
            ),
            yearly = CalculatorResponsePayPeriod(
                payPeriod = YEARLY,
                taxToPay = taxPayable.convertAmountFromYearlyToPayPeriod(YEARLY),
                employeesNI = employeesNI.convertAmountFromYearlyToPayPeriod(YEARLY),
                employersNI = employersNI.convertAmountFromYearlyToPayPeriod(YEARLY),
                wages = yearlyWages.convertAmountFromYearlyToPayPeriod(YEARLY),
                taxBreakdown = bandBreakdown,
                taxFree = taxFreeAmount,
                kCodeAdjustment = amountToAddToWages
            )
        )
    }

    private fun taxToPay(yearlyWages: Double, taxCode: TaxCode): Double {
        val taxBands = TaxBands(taxCode.country, taxYear).bands

        return when (taxCode) {
            is StandardTaxCode, is AdjustedTaxFreeTCode, is EmergencyTaxCode, is MarriageTaxCodes ->
                getTotalFromBands(adjustTaxBands(taxBands, taxCode), yearlyWages)
            is NoTaxTaxCode -> yearlyWages * taxCode.taxFreeAmount
            is SingleBandTax -> yearlyWages * taxBands[taxCode.taxAllAtBand].percentageAsDecimal
            is KTaxCode ->
                getTotalFromBands(adjustTaxBands(taxBands, taxCode), yearlyWages + taxCode.amountToAddToWages)
            else -> throw InvalidTaxCodeException("$this is an invalid tax code")
        }
    }

    private fun adjustTaxBands(taxBands: List<Band>, taxCode: TaxCode): List<Band> {
        // The full tax free amount e.g. 12509
        val bandAdjuster = getDefaultTaxAllowance(taxYear, taxCode.country)

        taxBands[0].upper = taxCode.taxFreeAmount
        taxBands[1].lower = taxCode.taxFreeAmount
        taxBands[1].upper = taxBands[1].upper + taxCode.taxFreeAmount - bandAdjuster

        for (bandNumber in 2 until taxBands.size) {
            taxBands[bandNumber].lower = taxBands[bandNumber].lower + taxCode.taxFreeAmount - bandAdjuster
            if (taxBands[bandNumber].upper != -1.0) {
                taxBands[bandNumber].upper = taxBands[bandNumber].upper + taxCode.taxFreeAmount - bandAdjuster
            }
        }
        return taxBands
    }

    private fun employerNIToPay(yearlyWages: Double) =
        if (pensionAge) 0.0 else getTotalFromBands(EmployerNIBands(taxYear).bands, yearlyWages)

    private fun employeeNIToPay(yearlyWages: Double) =
        if (pensionAge) 0.0 else getTotalFromBands(EmployeeNIBands(taxYear).bands, yearlyWages)

    private fun getTotalFromBands(bands: List<Band>, wages: Double): Double {
        var amount = 0.0
        bands.map { band: Band ->
            if (band.inBand(wages)) {
                val taxForBand = (wages - band.lower) * band.percentageAsDecimal
                if (shouldAddBand(band, band.percentageAsDecimal)) {
                    bandBreakdown.add(BandBreakdown(band.percentageAsDecimal, taxForBand))
                }
                amount += taxForBand
                return amount
            } else {
                val taxForBand = (band.upper - band.lower) * band.percentageAsDecimal
                if (shouldAddBand(band, band.percentageAsDecimal)) {
                    bandBreakdown.add(BandBreakdown(band.percentageAsDecimal, taxForBand))
                }
                amount += taxForBand
            }
        }
        throw InvalidTaxBandException("No tax bands were found to be used for the calculation")
    }

    private fun shouldAddBand(band: Band, percentage: Double) = band is TaxBand && percentage > 0.0

    companion object {
        fun getDefaultTaxCode() = "${(getDefaultTaxAllowance(TaxYear().currentTaxYear()) / 10)}L"

        fun isValidTaxCode(taxCode: String): Boolean {
            return try {
                taxCode.toTaxCode()
                true
            } catch (e: InvalidTaxCodeException) {
                false
            }
        }

        fun isAboveMinimumWages(wages: Double) = wages > 0

        fun isBelowMaximumWages(wages: Double) = wages < 9999999.99

        fun isAboveMinimumHoursPerWeek(hours: Double) = hours > 0

        fun isBelowMaximumHoursPerWeek(hours: Double) = hours <= 168

        internal fun getDefaultTaxAllowance(taxYear: Int, country: Country = ENGLAND) =
            TaxBands(country, taxYear).bands[0].upper.toInt()
    }
}
