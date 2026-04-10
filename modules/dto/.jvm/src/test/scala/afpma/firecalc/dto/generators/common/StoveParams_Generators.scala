/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.generators.common

import org.scalacheck.Gen
import afpma.firecalc.dto.common.{StoveParams, FacingType, InnerConstructionMaterial}
import afpma.firecalc.dto.generators.base.PrimitiveGenerators
import afpma.firecalc.units.coulombutils.*

trait StoveParams_Generators extends PrimitiveGenerators:

    def genSizingMethod: Gen[StoveParams.SizingMethod] =
        Gen.oneOf(StoveParams.SizingMethod.values)

    def genFacingType: Gen[FacingType] =
        Gen.oneOf(FacingType.WithAirGap, FacingType.WithoutAirGap)

    def genInnerConstructionMaterial: Gen[InnerConstructionMaterial] =
        Gen.const(InnerConstructionMaterial.WithinSpecs)

    def genStoveParams: Gen[StoveParams] =
        for
            sizingMethod <- genSizingMethod
            result <-
                sizingMethod match
                    case StoveParams.SizingMethod.MaxLoad =>
                        for
                            maxLoad <- genMaxLoad
                            heatingCycle <- genHeatingCycle
                            efficiency <- genEfficiency
                            facingType <- genFacingType
                        yield StoveParams.fromMaxLoadAndStoragePeriod(
                            maxLoad,
                            heatingCycle,
                            efficiency,
                            facingType
                        )
                    case StoveParams.SizingMethod.NominalHeatOutput =>
                        for
                            nominalOutput <- Gen.choose(3.0, 15.0).map(_.kW)
                            heatingCycle <- genHeatingCycle
                            efficiency <- genEfficiency
                            facingType <- genFacingType
                        yield StoveParams.fromNominalHeatOutput(
                            nominalOutput,
                            heatingCycle,
                            efficiency,
                            facingType
                        )
        yield result

end StoveParams_Generators
