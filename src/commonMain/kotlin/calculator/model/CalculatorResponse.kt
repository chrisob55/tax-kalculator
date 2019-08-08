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
package calculator.model

data class BandBreakdown(val percentage: Double, val amount: Double) {
    // TODO better way if percentage is a decimal. Awaiting Kotlin native library for MPP
    private val percentageFormatted = (percentage * 100).toInt()
    val bandDescription: String = "Income taxed at $percentageFormatted%"
}

data class CalculatorResponsePayPeriod(
    val payPeriod: PayPeriod,
    val taxToPay: Double,
    val employeesNI: Double,
    val employersNI: Double,
    val wages: Double,
    val taxBreakdown: List<BandBreakdown>,
    val taxFree: Double,
    val kCodeAdjustment: Double? = null
) {
    val totalDeductions: Double = taxToPay + employeesNI
    val takeHome: Double = wages - totalDeductions
}

data class CalculatorResponse(
    val taxCode: String,
    val country: Country,
    val isKCode: Boolean,
    val weekly: CalculatorResponsePayPeriod,
    val fourWeekly: CalculatorResponsePayPeriod,
    val monthly: CalculatorResponsePayPeriod,
    val yearly: CalculatorResponsePayPeriod
)