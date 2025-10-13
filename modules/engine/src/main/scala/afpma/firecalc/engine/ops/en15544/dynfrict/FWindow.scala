/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.ops.en15544.dynfrict

import cats.data.*
import cats.data.Validated.*
import cats.syntax.all.*

import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.pipedescr
import afpma.firecalc.engine.models.en15544.pipedescr.*
import afpma.firecalc.engine.models.en15544.shortsection.*
import afpma.firecalc.engine.models.en15544.shortsection.ShortOrRegularOps.given
import afpma.firecalc.engine.models.gtypedefs.*
import afpma.firecalc.engine.ops.DynamicFrictionCoeffOp.*
import afpma.firecalc.engine.ops.en15544.dynamicfrictioncoeff

private[dynfrict] opaque type SrcIdx = Int
private[dynfrict] object SrcIdx:
    def apply(i: Int): SrcIdx = i
    extension (ri: SrcIdx)
        def unwrap: Int = ri

private[dynfrict] opaque type CmprssdIdx = Int
private[dynfrict] object CmprssdIdx:
    def apply(i: Int): CmprssdIdx = i
    extension (ri: CmprssdIdx)
        def unwrap: Int = ri

private[dynfrict] type R = NamedPipeElDescrG[StraightSection] | NamedPipeElDescrG[DirectionChange]
private[dynfrict] type S_or_DC = StraightSection | DirectionChange

private[dynfrict] case class Named[+T](name: PipeName, t: T)

private[dynfrict] def asDirectionChange(pd: PipeElDescr): Option[DirectionChange] = 
    pd match
        case x: DirectionChange => Some(x)
        case _                  => None

// matchers
private[dynfrict] object SomeDirectionChange:
    def unapply(osdc: Option[S_or_DC]): Option[DirectionChange] = osdc match
        case Some(dc: DirectionChange) => Some(dc)
        case _ => None
private[dynfrict] object ShortStraightSection:
    def unapply(s: StraightSection): Option[StraightSection] = 
        s.shortOrRegular match
            case ShortOrRegular.Short   => Some(s)
            case ShortOrRegular.Regular => None

private[dynfrict] object RegularStraightSection:
    def unapply(s: StraightSection): Option[StraightSection] = 
        s.shortOrRegular match
            case ShortOrRegular.Short   => None
            case ShortOrRegular.Regular => Some(s)

private[dynfrict] final case class FWindow(
    nm3: Option[Named[S_or_DC]],  // n-3
    nm2: Option[Named[S_or_DC]],  // n-2
    nm1: Option[Named[S_or_DC]],  // n-1
    curr: Named[DirectionChange], // n
    np1: Option[Named[S_or_DC]],  // n+1
    np2: Option[Named[S_or_DC]],  // n+2
    np3: Option[Named[S_or_DC]]   // n+3
) {

    def coeffs_curr(using alg: ShortSectionAlg): ValidatedNel[Err, ζ] = 
        // if (nm3 && nm1) or (nm1 && np1) or (np1 && np3) are "short" sections
        // use level2 coeffs when two consecutive short sections
        if (
            (nm3.isShortStraightSection && nm1.isShortStraightSection) ||
            (nm1.isShortStraightSection && np1.isShortStraightSection) ||
            (np1.isShortStraightSection && np3.isShortStraightSection)
        )
            coeffs_level2_nm2_curr_np2.map(_._2)
        // if (nm3 is regular && nm1 is short) or (nm1 is short && np1 is regular) or (nm1 is regular && np1 is short) or (np1 is short && np3 is regular)
        // use level1 coeffs
        else if (
            (nm3.isRegularStraightSection && nm1.isShortStraightSection) ||
            (nm1.isShortStraightSection && np1.isRegularStraightSection) ||
            (nm1.isRegularStraightSection && np1.isShortStraightSection) ||
            (np1.isShortStraightSection && np3.isRegularStraightSection)
        )
            coeffs_level1_nm2_curr_np2.map(_._2)
        // otherwise, it means no short sections found
        else
            coeffs_level0_nm2_curr_np2.map(_._2)
            // use level0 coeffs

    def coeffs_level0_nm2_curr_np2: ValidatedNel[Err, (Option[ζ], ζ, Option[ζ])] = 
        val ζ_nm2_vo  = nm2.ζ_level_0
        val ζ_curr_v = dynamicfrictioncoeff.whenRegularFor(curr.t)
        val ζ_np2_vo  = np2.ζ_level_0

        (ζ_nm2_vo, ζ_curr_v, ζ_np2_vo).mapN: (ζ_nm2, ζ_curr, ζ_np2) =>
            (ζ_nm2, ζ_curr, ζ_np2)

    private def coeffs_level_n_plus_1_for_nm2_curr_np2(
        coeffs_level_n_for_nm2_curr_np2: ValidatedNel[Err, (Option[ζ], ζ, Option[ζ])]
    )(using 
        alg: ShortSectionAlg
    ): ValidatedNel[Err, (Option[ζ], ζ, Option[ζ])] = 
        // get level N
        coeffs_level_n_for_nm2_curr_np2 match
            // call en15544 with level0 coeffs and "modified" formulas
            case Valid((ζ_nm2_level_n_opt, ζ_curr_level_n, ζ_np2_level_n_opt)) =>

                // build windows w_nm1 and w_np1 as if both are 'short' sections
                // but sometimes, only one is truly a short section (we'll select proper coeff later according to which is short)
                val w_nm1_opt = ζ_nm2_level_n_opt.map(ζ_nm2_level_n => ShortSection.PipeDescrWindow.from(
                    ζα1_prev        =  ζ_nm2_level_n,
                    ζα2_prev        =  ζ_curr_level_n,
                    dc01            = nm2.map(_.t.asInstanceOf[DirectionChange]).get,
                    s1              = nm1.map(_.t.asInstanceOf[StraightSection]).get,
                    o_dc12          = curr.t.some,
                    o_dc12_name     = curr.name.some
                ))
                val w_np1_opt = ζ_np2_level_n_opt.map(ζ_np2_level_n => ShortSection.PipeDescrWindow.from(
                    ζα1_prev        =  ζ_curr_level_n,
                    ζα2_prev        =  ζ_np2_level_n,
                    dc01            = curr.t,
                    s1              = np1.map(_.t.asInstanceOf[StraightSection]).get,
                    o_dc12          = np2.map(_.t.asInstanceOf[DirectionChange]),
                    o_dc12_name     = np2.map(_.name),
                ))

                val r_nm1_v_opt = w_nm1_opt.map(_ andThen alg.resultFromWindow)
                val r_np1_v_opt = w_np1_opt.map(_ andThen alg.resultFromWindow)


                (r_nm1_v_opt, r_np1_v_opt) match
                    case (Some(r_nm1_v), Some(r_np1_v)) =>
                        (r_nm1_v, r_np1_v).mapN: (r_nm1, r_np1) =>
                            // TODO: require r_nm1.ζ2 == r_np1.ζ1 ???
                            val z_curr = 
                                if      (nm1.isShortStraightSection   && np1.isRegularStraightSection)  r_nm1.ζ2
                                else if (nm1.isRegularStraightSection && np1.isShortStraightSection)    r_np1.ζ1
                                else if (nm1.isShortStraightSection   && np1.isShortStraightSection)
                                    require(r_nm1.ζ2 == r_np1.ζ1, s"unexpected error: r_nm1.ζ2 != r_np1.ζ1 (${r_nm1.ζ2} != ${r_np1.ζ1})")
                                    r_np1.ζ1
                                else throw new Exception("should not happen")
                            (r_nm1.ζ1.some, z_curr, r_np1.ζ2.some)
                    case (None, Some(r_np1_v)) =>
                        r_np1_v.map: r_np1 =>
                            val z_curr = 
                                if      (nm1.isShortStraightSection   && np1.isRegularStraightSection)  ζ_curr_level_n
                                else if (nm1.isRegularStraightSection && np1.isShortStraightSection)    r_np1.ζ1
                                else if (nm1.isShortStraightSection   && np1.isShortStraightSection)    r_np1.ζ1
                                else throw new Exception("should not happen")
                            (None, z_curr, r_np1.ζ2.some)
                    case (Some(r_nm1_v), None) =>
                        r_nm1_v.map: r_nm1 =>
                            val z_curr = 
                                if      (nm1.isShortStraightSection   && np1.isRegularStraightSection)  r_nm1.ζ2
                                else if (nm1.isRegularStraightSection && np1.isShortStraightSection)    ζ_curr_level_n
                                else if (nm1.isShortStraightSection   && np1.isShortStraightSection)    r_nm1.ζ2
                                else throw new Exception("should not happen")
                            (r_nm1.ζ1.some, z_curr, None)
                    case (None, None) =>
                        (None, ζ_curr_level_n, None).validNel

            case i @ Invalid(_) => i

    def coeffs_level1_nm2_curr_np2(using alg: ShortSectionAlg): ValidatedNel[Err, (Option[ζ], ζ, Option[ζ])] = 
        coeffs_level_n_plus_1_for_nm2_curr_np2(coeffs_level0_nm2_curr_np2)

    def coeffs_level2_nm2_curr_np2(using alg: ShortSectionAlg): ValidatedNel[Err, (Option[ζ], ζ, Option[ζ])] = 
        coeffs_level_n_plus_1_for_nm2_curr_np2(coeffs_level1_nm2_curr_np2)

    extension (n_sdc: Option[Named[S_or_DC]])
        def ζ_level_0: ValidatedNel[Err, Option[ζ]] =
            n_sdc.map(_.t).map(dynamicfrictioncoeff.whenRegularFor) match
                case Some(Valid(c))       => Some(c).validNel
                case Some(i @ Invalid(_)) => i
                case None                 => None.validNel
        def isDirectionChange: Boolean = n_sdc match
            case Some(_ @ Named(_, _: DirectionChange)) => true
            case _                                      => false
        def isStraightSection: Boolean = n_sdc match
            case Some(_ @ Named(_, _: StraightSection)) => true
            case _                                      => false

        def isShortStraightSection: Boolean = n_sdc match
            case Some(_ @ Named(_, s: StraightSection)) => s.isShort
            case _                                      => false

        def isRegularStraightSection: Boolean = n_sdc match
            case Some(_ @ Named(_, s: StraightSection)) => s.isRegular
            case _                                      => false

    def check(descr: String)(cond: Boolean) = 
        if (cond) this.asRight else IllegalStateException(descr).asLeft

    def makeChecks: Either[IllegalStateException, FWindow] =
        for 
            _   <- check("does not start with a direction change")(!(nm3.isEmpty && nm2.isEmpty && nm1.isEmpty))
            _   <- check("pipe can not end with a direction change")(!(nm1.isDefined && np1.isEmpty))
            _   <- check("two successive 'direction change' should not be allowed")(
                !(
                    (nm3.isDirectionChange && nm2.isDirectionChange) ||
                    (nm2.isDirectionChange && nm1.isDirectionChange) ||
                    (nm1.isDirectionChange) ||
                    (np1.isDirectionChange) ||
                    (np1.isDirectionChange && np2.isDirectionChange) ||
                    (np2.isDirectionChange && np3.isDirectionChange)
                )
            )
            _   <- check("two successive 'straight section' should not be allowed")(
                !(
                    (nm3.isStraightSection && nm2.isStraightSection) ||
                    (nm2.isStraightSection && nm1.isStraightSection) ||
                    (np1.isStraightSection && np2.isStraightSection) ||
                    (np2.isStraightSection && np3.isStraightSection)
                )
            )
            _   <- check("holes should not happen")(
                !(
                    (nm3.isDefined && (nm2.isEmpty || nm1.isEmpty)) ||
                    (nm2.isDefined && nm1.isEmpty)
                )
            )
            ret <- this.asRight
        yield ret
    
}

private[dynfrict] object FWindow:

    def make(
        nm3: Option[Named[S_or_DC]],  // n-3
        nm2: Option[Named[S_or_DC]],  // n-2
        nm1: Option[Named[S_or_DC]],  // n-1
        curr: Named[DirectionChange], // n
        np1: Option[Named[S_or_DC]],  // n+1
        np2: Option[Named[S_or_DC]],  // n+2
        np3: Option[Named[S_or_DC]]   // n+3
    ): Either[IllegalStateException, FWindow] = 
        FWindow(nm3, nm2, nm1, curr, np1, np2, np3).makeChecks

