/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.ops.en15544.dynfrict

import cats.data.*
import cats.data.Validated.Invalid
import cats.syntax.all.*

import algebra.instances.all.given

import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.pipedescr.*
import afpma.firecalc.engine.models.en15544.shortsection.ShortSectionAlg
import afpma.firecalc.engine.ops.DynamicFrictionCoeffOp
import afpma.firecalc.engine.ops.DynamicFrictionCoeffOp.Result
import afpma.firecalc.engine.ops.en15544.dynfrict.*
import afpma.firecalc.engine.standard.*

import coulomb.ops.standard.all.given

case class DynamicFrictionCoeffOpForConcatenatedPipeVector(
    pipesConcat: Vector[NamedPipeElDescrG[PipeElDescr]]
)(using SSAlg: ShortSectionAlg) extends DynamicFrictionCoeffOp[NamedPipeElDescrG[DirectionChange]] 
{
    // merge successive straight sections into a single one
    // and keep only direction change + straight section elements
    // also keeps original source index so that the computed coefficient for an element in src
    // can be easily computed using this struct
    private val vcompressed: ValidatedNel[PressureLossCoeff_Error, Vector[(CmprssdIdx, R)]] = 
    {
        val filtered = 
            pipesConcat
                .mapFilter[NamedPipeElDescrG[StraightSection | DirectionChange]]: nel =>
                    import nel.*
                    nel.el match
                        case el: StraightSection => 
                                Some(NamedPipeElDescrG(idx, typ, nel.name, el, nf))
                        case el: DirectionChange => 
                                Some(NamedPipeElDescrG(idx, typ, nel.name, el, nf))
                        case _ => None
        val vreduced = 
            val zero: ValidatedNel[PressureLossCoeff_Error, Vector[R]] = Vector.empty[R].validNel
            filtered.foldLeft[ValidatedNel[PressureLossCoeff_Error, Vector[R]]](zero): (vacc, nel) =>
                vacc match 
                    case i @ Validated.Invalid(_) => i
                    case Validated.Valid(acc) =>
                        acc.lastOption match
                            case None => 
                                nel.el match
                                    case dc: DirectionChange => Vector(nel.copy(el = dc)).validNel
                                    case s: StraightSection  => Vector(nel.copy(el = s)).validNel

                            case Some(lastNel) =>
                                lastNel.el match
                                    case ls: StraightSection =>
                                        nel.el match
                                            case currStraight: StraightSection =>
                                                if (ls.geometry.dh == currStraight.geometry.dh)
                                                    // only merge when same hydraulic diameter
                                                    val merged = lastNel.copy(
                                                        name = nel.name.appendString(" ++ ").appendPipeName(lastNel.name),
                                                        el = ls.copy(length = (currStraight.length + ls.length))
                                                    )
                                                    acc.dropRight(1).appended(merged).validNel
                                                else
                                                    val currStraightOrigIdx = pipesConcat.indexWhere(x => x.typ == nel.typ && x.idx == nel.idx)
                                                    val lastStraightOrigIdx = pipesConcat.indexWhere(x => x.typ == lastNel.typ && x.idx == lastNel.idx)
                                                    val inBetweenElements = pipesConcat.slice(lastStraightOrigIdx+1, currStraightOrigIdx)
                                                    val sectionGeomChangeElems = inBetweenElements.mapFilter[NamedPipeElDescrG[SectionGeometryChange]]: nel =>
                                                        nel.el match
                                                            case el: SectionGeometryChange => 
                                                                Some(NamedPipeElDescrG(nel.idx, nel.typ, nel.name, el, nel.nf))
                                                            case _ => 
                                                                None

                                                    if (sectionGeomChangeElems.nonEmpty)
                                                        //
                                                        // TODO: how to handle multiple "short" sections combined with section change  in geometry ??
                                                        //
                                                        // Quick solution :
                                                        // Merge and take the highest hydraulic diameter,
                                                        // Influence of short section will be the highest see 4.9.5 in EN 15544:2023
                                                        val merged = lastNel.copy(
                                                            name = nel.name.appendString(" ++ ").appendPipeName(lastNel.name),
                                                            el = ls.copy(
                                                                length = (currStraight.length + ls.length),
                                                                geometry = 
                                                                    if (ls.geometry.dh >= currStraight.geometry.dh) ls.geometry
                                                                    else currStraight.geometry
                                                            )
                                                        )
                                                        acc.dropRight(1).appended(merged).validNel
                                                    else
                                                        // different hydraulic diameter + no section geometry change in between
                                                        // should not happen : section geometry change should be automatically appended if user did not specify them
                                                        LocalStructError(
                                                            s"missing section geometry change : current section '${nel.fullRef}' (dh = ${currStraight.geometry.dh}) AND last section '${lastNel.fullRef}' (dh = ${ls.geometry.dh})"
                                                        ).invalidNel[Vector[R]]
                                            case dc: DirectionChange =>
                                                acc.appended(nel.copy(el = dc)).validNel
                                    
                                    case _: DirectionChange =>
                                        nel.el match
                                            case _: DirectionChange => 
                                                LocalStructError(
                                                    "not allowed : two consecutive turns without an element.")
                                                .invalidNel[Vector[R]]
                                            case s: StraightSection  => acc.appended(nel.copy(el = s)).validNel
        
        vreduced.map(_.mapWithIndex: (r, idx) => 
                (CmprssdIdx(idx), r)
        )
        
    }
            
    // def findByIdxAndType(idx: PipeIdx, typ: PipeType): Option[NamedPipeElDescrG[PipeElDescr]] = 
    //     pipesConcat.find(e => e.idx == idx && e.typ == typ)

    private def findLocalCmprssdIdx(
        ndc: NamedPipeElDescrG[DirectionChange]
    ): ValidatedNel[PressureLossCoeff_Error, Option[CmprssdIdx]] = 
        vcompressed.map: compressed =>
            compressed
                .find: (_, rdc) => 
                    rdc.el match
                        case _: DirectionChange =>
                            rdc.idx == ndc.idx && rdc.typ == ndc.typ
                        case _: StraightSection => false
                .map(_._1)

    private def computeCoeffForDirectionChange(ndc: NamedPipeElDescrG[DirectionChange]): DynamicFrictionCoeffOp.Result = 
        findLocalCmprssdIdx(ndc).andThen:
            case None       => LocalStructError(s"${ndc.name} not found in local struct").invalidNel
            case Some(ridx) => localComputeCoeff(ridx)
    
    extension (a: NamedPipeElDescrG[DirectionChange]) def dynamicFrictionCoeff: Result = 
        computeCoeffForDirectionChange(a)

    private def getFWindow(
        cmprssdIdx: CmprssdIdx
    ): ValidatedNel[PressureLossCoeff_Error, FWindow] = 
        val cidx = cmprssdIdx.unwrap
        def extractNeeded(r: R): Named[S_or_DC] = r.el match
            case ss: StraightSection => Named(r.name, ss)
            case dc: DirectionChange => Named(r.name, dc)

        vcompressed
        .andThen: compressed =>

            val vcenter = compressed
                .get(cidx    ).map(_._2)
                .map(extractNeeded) match
                    case None                                       => LocalStructError("dev error : missing in index in local struct").invalidNel
                    case Some(Named(_, _: StraightSection))         => LocalStructError("getFWindow can only be called on a DirectionChange").invalidNel
                    case Some(ndc @ Named(_, dc: DirectionChange))  => ndc.copy(t = dc).validNel
                
            vcenter.andThen: center =>
                FWindow.make(
                    compressed.get(cidx - 3).map(_._2).map(extractNeeded),
                    compressed.get(cidx - 2).map(_._2).map(extractNeeded),
                    compressed.get(cidx - 1).map(_._2).map(extractNeeded),
                    center,
                    compressed.get(cidx + 1).map(_._2).map(extractNeeded),
                    compressed.get(cidx + 2).map(_._2).map(extractNeeded),
                    compressed.get(cidx + 3).map(_._2).map(extractNeeded)
                ).fold(
                    err => LocalStructError(err.getMessage()).invalidNel,
                    _.validNel
                )
        

    private def localComputeCoeff(cmprssdIdx: CmprssdIdx): DynamicFrictionCoeffOp.Result = 
        getFWindow(cmprssdIdx).andThen(_.coeffs_curr)

            // case FWindow(o_nm2, Some(nm1 @ Named(_, _: StraightSection)), curr, Some(np1 @ Named(_, _: StraightSection)), o_np2) => 

            //     (o_nm2, nm1.asInstanceOf[Named[StraightSection]], np1.asInstanceOf[Named[StraightSection]]) match

            //         case (_                         , Named(_, RegularStraightSection(nm1))   , Named(_, RegularStraightSection(np1))) =>
            //             afpma.firecalc.engine.ops.en15544.dynamicfrictioncoeff.whenRegularFor(curr.t)

            //         case (_                         , Named(_, RegularStraightSection(nm1))   , Named(_, ShortStraightSection(np1))) => 
            //             PipeDescrWindow
            //                 .from(
            //                     ζ2_modified_WhenPreviousSectionIsShort  = None,
            //                     dc01                                    = curr.t,
            //                     s1                                      = np1,
            //                     o_dc12                                  = o_np2.map(_.t).flatMap(asDirectionChange),
            //                     o_dc12_name                             = o_np2.map(_.name))
            //                 .andThen(SSAlg.resultFromWindow)
            //                 .map(_.ζ1)

            //         // pipe starts with a "short" section and following is regular : consider previous direction change has angle = 0 degrees
            //         case (None                      , Named(_, ShortStraightSection(nm1))     , Named(_, RegularStraightSection(np1))) => 
            //             PipeDescrWindow
            //                 .from(
            //                     ζ2_modified_WhenPreviousSectionIsShort  = None,
            //                     dc01                                    = DirectionChange.angleVifZero,
            //                     s1                                      = nm1,
            //                     o_dc12                                  = Some(curr.t),
            //                     o_dc12_name                             = Some(curr.name))
            //                 .andThen(SSAlg.resultFromWindow)
            //                 .map(_.ζ2)

            //         // pipe starts with a "short" section and following is also "short" : consider previous direction change has angle = 0 degrees
            //         case (None                      , Named(_, ShortStraightSection(nm1))     , Named(_, ShortStraightSection(np1))) => 
            //             val ζ2_modified_WhenPreviousSectionIsShort: Option[ζ] =
            //                 PipeDescrWindow
            //                     .from(
            //                         ζ2_modified_WhenPreviousSectionIsShort  = None,
            //                         dc01                                    = DirectionChange.angleVifZero,
            //                         s1                                      = nm1,
            //                         o_dc12                                  = Some(curr.t),
            //                         o_dc12_name                             = Some(curr.name))
            //                     .andThen(SSAlg.resultFromWindow)
            //                     .map(_.ζ2)
            //                     .toOption
            //             PipeDescrWindow
            //                 .from(
            //                     ζ2_modified_WhenPreviousSectionIsShort  = ζ2_modified_WhenPreviousSectionIsShort,
            //                     dc01                                    = curr.t,
            //                     s1                                      = np1,
            //                     o_dc12                                  = o_np2.map(_.t).flatMap(asDirectionChange),
            //                     o_dc12_name                             = o_np2.map(_.name))
            //                 .andThen(SSAlg.resultFromWindow)
            //                 .map(_.ζ1)
                    
            //         case (Some(Named(_, _: DirectionChange)) , Named(_, ShortStraightSection(_))     , Named(_, ShortStraightSection(np1))) => 
            //             localComputeCoeff(CmprssdIdx(cmprssdIdx.unwrap - 2)) andThen { ζ2_modified_WhenPreviousSectionIsShort =>
            //                 PipeDescrWindow
            //                     .from(
            //                         ζ2_modified_WhenPreviousSectionIsShort  = Some(ζ2_modified_WhenPreviousSectionIsShort),
            //                         dc01                                    = curr.t,
            //                         s1                                      = np1,
            //                         o_dc12                                  = o_np2.map(_.t).flatMap(asDirectionChange),
            //                         o_dc12_name                             = o_np2.map(_.name))
            //                     .andThen(SSAlg.resultFromWindow)
            //                     .map(_.ζ1)
            //             }

            //         case (Some(Named(_, nm2_dc: DirectionChange)) , Named(_, ShortStraightSection(nm1))     , Named(_, RegularStraightSection(np1))) => 
            //             PipeDescrWindow
            //                 .from(
            //                     ζ2_modified_WhenPreviousSectionIsShort  = None,
            //                     dc01                                    = nm2_dc,
            //                     s1                                      = nm1,
            //                     o_dc12                                  = Some(curr.t),
            //                     o_dc12_name                             = Some(curr.name))
            //                 .andThen(SSAlg.resultFromWindow)
            //                 .map(_.ζ2)
}



