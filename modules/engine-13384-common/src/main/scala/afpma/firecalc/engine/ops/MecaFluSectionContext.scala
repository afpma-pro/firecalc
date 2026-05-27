package afpma.firecalc.engine.ops

import afpma.firecalc.units.coulombutils.*
import afpma.firecalc.dto.v4.AirSpaceDetailed_V2
import afpma.firecalc.dto.common.PipeShape
import afpma.firecalc.engine.models.*

case class MecaFluSectionContext[PipeEl <: Matchable, Params](
    gasInPipeEl        : GasInPipeEl[NamedPipeElDescrG[PipeEl], Gas, Params],
    gasTempStart       : TCelsius,
    lastPipeDensity    : Option[Density],
    lastPipeVelocity   : Option[FlowVelocity],
    lastAirSpaceDetailed: Option[AirSpaceDetailed_V2],
    lastCrossSectionArea: Option[Area],
    lastInnerGeom      : Option[PipeShape],
    prevSectionResult  : Option[PipeSectionResult[PipeEl]]
)

object MecaFluSectionContext:
    def initial[PipeEl <: Matchable, Params](
        gip  : GasInPipeEl[NamedPipeElDescrG[PipeEl], Gas, Params],
        temp : TCelsius
    ): MecaFluSectionContext[PipeEl, Params] =
        MecaFluSectionContext(
            gasInPipeEl        = gip,
            gasTempStart       = temp,
            lastPipeDensity    = None,
            lastPipeVelocity   = None,
            lastAirSpaceDetailed = None,
            lastCrossSectionArea = None,
            lastInnerGeom      = None,
            prevSectionResult  = None
        )

    def update[PipeEl <: Matchable, Params](
        ctx     : MecaFluSectionContext[PipeEl, Params],
        section : PipeSectionResult[PipeEl],
        nextGip : GasInPipeEl[NamedPipeElDescrG[PipeEl], Gas, Params]
    ): MecaFluSectionContext[PipeEl, Params] =
        ctx.copy(
            gasInPipeEl        = nextGip,
            gasTempStart       = section.gas_temp_end,
            lastAirSpaceDetailed = section.air_space_detailed.orElse(ctx.lastAirSpaceDetailed),
            lastCrossSectionArea = Some(section.crossSectionArea_end),
            lastInnerGeom      = Some(section.innerShape_end),
            prevSectionResult  = Some(section)
        )

    def updatePreserveUpstream[PipeEl <: Matchable, Params](
        ctx     : MecaFluSectionContext[PipeEl, Params],
        section : PipeSectionResult[PipeEl],
        nextGip : GasInPipeEl[NamedPipeElDescrG[PipeEl], Gas, Params]
    ): MecaFluSectionContext[PipeEl, Params] =
        ctx.copy(
            gasInPipeEl        = nextGip,
            gasTempStart       = section.gas_temp_end,
            lastAirSpaceDetailed = section.air_space_detailed.orElse(ctx.lastAirSpaceDetailed),
            lastCrossSectionArea = Some(section.crossSectionArea_end),
            lastInnerGeom      = Some(section.innerShape_end),
            prevSectionResult  = Some(section)
        )
