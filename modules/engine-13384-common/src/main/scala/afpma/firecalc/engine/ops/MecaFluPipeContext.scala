package afpma.firecalc.engine.ops

import afpma.firecalc.units.coulombutils.*
import afpma.firecalc.engine.models.*

case class MecaFluPipeContext[PipeEl <: Matchable](
    fullDescr      : PipeFullDescrG[PipeEl],
    gas            : Gas,
    gasTempStart   : TCelsius,
    lastPipeDensity: Option[Density],
    lastPipeVelocity: Option[FlowVelocity]
)
