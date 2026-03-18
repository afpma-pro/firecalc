/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

import * as THREE from 'three'
import { OrbitControls } from 'three/addons/controls/OrbitControls.js'
import { ViewHelper } from 'three/addons/helpers/ViewHelper.js'

// =============================================================================
// TypeScript Interfaces
// =============================================================================

export interface PipeOrigin {
  x: number
  y: number
  z: number
}

export interface PipeDirection {
  dx: number
  dy: number
  dz: number
}

export type PipeShape =
  | { type: 'square'; side: number }
  | { type: 'rectangle'; width: number; height: number }
  | { type: 'circle'; diameter: number }

export interface PipeData {
  origin: PipeOrigin
  direction: PipeDirection
  length: number
  color: string
  shape: PipeShape
  shapeOrientation?: number // degrees, default 0
  lineIndex: number
  name?: string
  displayName?: string
}

export interface PipeGroup {
  pipes: PipeData[]
  name?: string
}

export interface VizConfig {
  canvasWidth?: number // fallback if container has no intrinsic size (default 750)
  canvasHeight?: number // fallback if container has no intrinsic size (default 600)
  shapeColor: string // e.g. "#FF6600"
  hoverColor: string // e.g. "#FFAA44"
  backgroundColor: string // e.g. "#F0F0F0"
  displayType: 'CenterLine' | 'FullShape' | 'Mixed'
  viewPadding?: number // multiplier for initial camera distance (default 1.5)
  mixedShapeOpacity?: number // opacity for shapes in Mixed mode (default 0.3)
  centerLineSphereRadius?: number // sphere radius for centerlines (default 1)
  centerLineStrokeWidth?: number // line width for centerlines (default 1)
  displayName?: boolean // whether to display pipe names as labels
  displayNameInModes?: Array<'CenterLine' | 'FullShape' | 'Mixed'> // modes in which to show names (default ['CenterLine'])
  nameVerticalOffset?: number // vertical offset in scene units for name labels (default 3)
  watermark?: string // optional watermark text to display at bottom center
  _cameraState?: { position: [number, number, number]; up: [number, number, number]; target: [number, number, number] }
  labelResetView?: string // label for reset view button (default 'reset view')
  labelViewMode?: string // label for view mode button (default 'view mode')
  labelAnnotations?: string // label for annotations button (default 'annotations')
  labelAxisRear?: string // label for the rear axis in the orientation gizmo (default 'rear')
  labelAxisUp?: string // label for the up axis in the orientation gizmo (default 'up')
  labelAxisRight?: string // label for the right axis in the orientation gizmo (default 'right')
}

export interface FilaireVizHandle {
  dispose(): void
  getCameraState(): VizConfig['_cameraState']
}

// =============================================================================
// Coordinate Transformation
// =============================================================================

/**
 * Converts construction coordinates (Z-up, Y-rear) to Three.js coordinates (Y-up):
 * - X → X
 * - Z (Up) → Y
 * - Y (Rear) → −Z  (Three.js +Z points toward viewer; construction rear goes AWAY from viewer)
 */
function toThreePos(origin: PipeOrigin): THREE.Vector3 {
  // Construction (X=right, Y=rear, Z=up) → Three.js right-handed (X=rear, Y=up, Z=right)
  return new THREE.Vector3(origin.y, origin.z, origin.x)
}

function toThreeDir(dir: PipeDirection): THREE.Vector3 {
  return new THREE.Vector3(dir.dy, dir.dz, dir.dx).normalize()
}

// =============================================================================
// Cross-Section to THREE.Shape
// =============================================================================

// =============================================================================
// Geometry Utilities for Miter Pipe Joining
// =============================================================================

/**
 * Computes the normal vector for the miter plane at a pipe junction.
 *
 * For a proper miter joint, the cutting plane should bisect the angle between
 * the two pipe directions. The angle bisector is computed as normalize(d1 + d2),
 * which creates a plane that cuts both pipes at the same angle, allowing them
 * to join seamlessly without gaps.
 *
 * @param currentDir - Unit direction vector of the current pipe segment
 * @param neighborDir - Unit direction vector of the neighboring pipe segment, or null for a flat end
 * @returns The normalized angle bisector vector (miter plane normal)
 *
 * Math:
 * - For a miter joint: bisector = normalize(normalize(currentDir) + normalize(neighborDir))
 * - For a flat end (neighborDir = null): normal = currentDir (perpendicular to pipe)
 * - Edge case: If directions are parallel (collinear), returns currentDir
 */
function computeMiterNormal(
  currentDir: THREE.Vector3,
  neighborDir: THREE.Vector3 | null
): THREE.Vector3 {
  if (neighborDir === null) {
    // Flat end: normal is perpendicular to the pipe direction
    return currentDir.clone().normalize()
  }

  // For a proper miter joint, the cutting plane should bisect the angle
  // between the two pipe directions. The bisector is: normalize(d1 + d2)
  const bisector = currentDir.clone().normalize()
    .add(neighborDir.clone().normalize())
  
  // Check for collinear pipes (parallel or anti-parallel)
  // If the bisector is nearly zero, the pipes are anti-parallel
  const epsilon = 1e-6
  if (bisector.lengthSq() < epsilon * epsilon) {
    // Collinear case: use flat end (perpendicular to direction)
    return currentDir.clone().normalize()
  }

  // Return the normalized angle bisector
  return bisector.normalize()
}

/**
 * Constructs a local coordinate frame perpendicular to the pipe direction.
 * 
 * This creates a right-handed coordinate system where the pipe direction is the
 * forward axis, and we compute perpendicular 'right' and 'up' vectors.
 * 
 * @param direction - Unit direction vector of the pipe
 * @returns Object containing normalized 'right' and 'up' vectors
 * 
 * Math:
 * - right = normalize(direction × worldUp)
 * - up = direction × right
 * - Fallback: If direction is parallel to worldUp, use worldForward as reference
 */
function buildLocalFrame(
  direction: THREE.Vector3,
  orientationDeg: number = 0
): {
  right: THREE.Vector3
  up: THREE.Vector3
} {
  const worldUp = new THREE.Vector3(0, 1, 0)
  const worldForward = new THREE.Vector3(0, 0, 1)
  
  // Compute default right vector: direction × worldUp
  let right = new THREE.Vector3().crossVectors(direction, worldUp)
  
  // Check if direction is parallel to worldUp (cross product would be zero)
  const epsilon = 1e-6
  if (right.lengthSq() < epsilon * epsilon) {
    // Fallback: use worldForward instead of worldUp
    right = new THREE.Vector3().crossVectors(direction, worldForward)
  }
  
  right.normalize()
  
  // Compute default up vector: direction × right (ensures right-handed system)
  const up = new THREE.Vector3().crossVectors(direction, right).normalize()
  
  // Apply orientation rotation around the pipe direction axis
  // Rotate both right and up vectors by θ (orientationDeg)
  if (orientationDeg !== 0) {
    const thetaRad = (orientationDeg * Math.PI) / 180
    const cos = Math.cos(thetaRad)
    const sin = Math.sin(thetaRad)
    
    // Rotation matrix for 2D rotation in the (right, up) plane:
    // rotatedRight = cos(θ) * right + sin(θ) * up
    // rotatedUp = -sin(θ) * right + cos(θ) * up
    const rotatedRight = right.clone().multiplyScalar(cos).add(up.clone().multiplyScalar(sin))
    const rotatedUp = right.clone().multiplyScalar(-sin).add(up.clone().multiplyScalar(cos))
    
    return { right: rotatedRight, up: rotatedUp }
  }
  
  return { right, up }
}

/**
 * Generates 2D vertices for a pipe cross-section shape in local coordinates.
 * 
 * Returns vertices in a local 2D coordinate system where the pipe centerline
 * is at (0, 0). These can be transformed into 3D space using a local frame.
 * 
 * @param shape - The pipe shape specification (square, rectangle, or circle)
 * @returns Array of 2D points {x, y} representing the cross-section vertices
 * 
 * Details:
 * - Square/Rectangle: 4 corner vertices
 * - Circle: 32 evenly spaced vertices around the circumference
 */
function generateCrossSectionVertices2D(
  shape: PipeShape
): Array<{ x: number; y: number }> {
  const vertices: Array<{ x: number; y: number }> = []

  switch (shape.type) {
    case 'square': {
      const h = shape.side / 2
      vertices.push(
        { x: -h, y: -h },
        { x: h, y: -h },
        { x: h, y: h },
        { x: -h, y: h }
      )
      break
    }

    case 'rectangle': {
      const w2 = shape.width / 2
      const h2 = shape.height / 2
      vertices.push(
        { x: -w2, y: -h2 },
        { x: w2, y: -h2 },
        { x: w2, y: h2 },
        { x: -w2, y: h2 }
      )
      break
    }

    case 'circle': {
      const radius = shape.diameter / 2
      const segments = 64
      for (let i = 0; i < segments; i++) {
        const angle = (i / segments) * Math.PI * 2
        vertices.push({
          x: Math.cos(angle) * radius,
          y: Math.sin(angle) * radius
        })
      }
      break
    }
  }

  return vertices
}

/**
 * Builds a custom BufferGeometry for a pipe segment with mitered end faces.
 * 
 * This function creates a 3D mesh by:
 * 1. Generating a 2D cross-section profile from the pipe shape
 * 2. Projecting the profile onto start and end miter planes
 * 3. Building side faces (quads) between start and end vertices
 * 4. Building cap faces using fan triangulation from the center
 * 
 * The miter plane allows pipe segments to join seamlessly at angles, as the
 * cut face follows the miter normal rather than being perpendicular to the pipe.
 * 
 * @param shape - The pipe cross-section shape (square, rectangle, or circle)
 * @param origin - The starting point of the pipe segment in 3D space
 * @param direction - The unit direction vector of the pipe
 * @param length - The length of the pipe segment
 * @param startMiterNormal - Normal vector to the miter plane at the start
 * @param endMiterNormal - Normal vector to the miter plane at the end
 * @returns BufferGeometry with positions and computed vertex normals
 * 
 * Math Details:
 * - For each cross-section vertex at 2D position (x, y):
 *   - Convert to 3D: c = origin + right*x + up*y
 *   - Project onto miter plane: t = -(c - planeOrigin) · miterNormal / direction · miterNormal
 *   - Final position: V = c + t * direction
 * 
 * Edge Cases:
 * - If direction · miterNormal ≈ 0, the miter plane is perpendicular to pipe direction.
 *   In this case, we fall back to a flat cut (t = 0) to avoid division by zero.
 */
function buildMiteredPipeGeometry(
  shape: PipeShape,
  origin: THREE.Vector3,
  direction: THREE.Vector3,
  length: number,
  startMiterNormal: THREE.Vector3,
  endMiterNormal: THREE.Vector3,
  orientationDeg: number = 0
): THREE.BufferGeometry {
  // Step 1: Build local frame (perpendicular axes) with orientation
  const { right, up } = buildLocalFrame(direction, orientationDeg)
  
  // Step 2: Generate cross-section vertices in 2D
  const crossSection2D = generateCrossSectionVertices2D(shape)
  const numVertices = crossSection2D.length
  
  // Step 3: Compute start vertices (projected onto start miter plane)
  const startVertices: THREE.Vector3[] = []
  for (let i = 0; i < crossSection2D.length; i++) {
    const { x, y } = crossSection2D[i]
    // Convert 2D vertex to 3D position on the pipe surface at origin
    const c = origin.clone()
      .add(right.clone().multiplyScalar(x))
      .add(up.clone().multiplyScalar(y))
    
    // Project onto start miter plane
    // Plane equation: (point - origin) · startMiterNormal = 0
    // Line equation: point = c + t * direction
    // Solve for t: (c + t*direction - origin) · startMiterNormal = 0
    //             => t = -(c - origin) · startMiterNormal / (direction · startMiterNormal)
    const numerator = c.clone().sub(origin).dot(startMiterNormal)
    const denominator = direction.dot(startMiterNormal)
    
    // Edge case: miter plane perpendicular to direction (parallel to pipe axis)
    const epsilon = 1e-6
    let t = 0
    if (Math.abs(denominator) > epsilon) {
      t = -numerator / denominator
    }
    
    const startVertex = c.clone().add(direction.clone().multiplyScalar(t))
    startVertices.push(startVertex)
  }
  
  // Step 4: Compute end vertices (projected onto end miter plane)
  const endPoint = origin.clone().add(direction.clone().multiplyScalar(length))
  const endVertices: THREE.Vector3[] = []
  
  for (let i = 0; i < crossSection2D.length; i++) {
    const { x, y } = crossSection2D[i]
    // Convert 2D vertex to 3D position on the pipe surface at end point
    const c = endPoint.clone()
      .add(right.clone().multiplyScalar(x))
      .add(up.clone().multiplyScalar(y))
    
    // Project onto end miter plane
    const numerator = c.clone().sub(endPoint).dot(endMiterNormal)
    const denominator = direction.dot(endMiterNormal)
    
    // Edge case: miter plane perpendicular to direction
    const epsilon = 1e-6
    let t = 0
    if (Math.abs(denominator) > epsilon) {
      t = -numerator / denominator
    }
    
    const endVertex = c.clone().add(direction.clone().multiplyScalar(t))
    endVertices.push(endVertex)
  }
  
  // Step 5: Build triangles
  const positions: number[] = []
  
  // 5a. Side faces (quads between start and end, split into 2 triangles each)
  for (let i = 0; i < numVertices; i++) {
    const next = (i + 1) % numVertices
    
    const v0 = startVertices[i]      // Bottom-left
    const v1 = startVertices[next]   // Bottom-right
    const v2 = endVertices[next]     // Top-right
    const v3 = endVertices[i]        // Top-left
    
    // Triangle 1: v0, v1, v2
    positions.push(v0.x, v0.y, v0.z)
    positions.push(v1.x, v1.y, v1.z)
    positions.push(v2.x, v2.y, v2.z)
    
    // Triangle 2: v0, v2, v3
    positions.push(v0.x, v0.y, v0.z)
    positions.push(v2.x, v2.y, v2.z)
    positions.push(v3.x, v3.y, v3.z)
  }
  
  // 5b. Start cap face (fan triangulation from center)
  const startCenter = new THREE.Vector3()
  for (const v of startVertices) {
    startCenter.add(v)
  }
  startCenter.divideScalar(numVertices)
  
  for (let i = 0; i < numVertices; i++) {
    const next = (i + 1) % numVertices
    
    // Triangle: center, v[next], v[i]
    // Winding order: CCW when viewed from outside (opposite to pipe direction)
    positions.push(startCenter.x, startCenter.y, startCenter.z)
    positions.push(startVertices[next].x, startVertices[next].y, startVertices[next].z)
    positions.push(startVertices[i].x, startVertices[i].y, startVertices[i].z)
  }
  
  // 5c. End cap face (fan triangulation from center)
  const endCenter = new THREE.Vector3()
  for (const v of endVertices) {
    endCenter.add(v)
  }
  endCenter.divideScalar(numVertices)
  
  for (let i = 0; i < numVertices; i++) {
    const next = (i + 1) % numVertices
    
    // Triangle: center, v[i], v[next]
    // Winding order: CCW when viewed from outside (along pipe direction)
    positions.push(endCenter.x, endCenter.y, endCenter.z)
    positions.push(endVertices[i].x, endVertices[i].y, endVertices[i].z)
    positions.push(endVertices[next].x, endVertices[next].y, endVertices[next].z)
  }
  
  // Step 6: Create BufferGeometry
  const geometry = new THREE.BufferGeometry()
  
  // Set position attribute (3 floats per vertex)
  const positionArray = new Float32Array(positions)
  geometry.setAttribute('position', new THREE.BufferAttribute(positionArray, 3))
  
  // Compute vertex normals for smooth lighting
  geometry.computeVertexNormals()
  
  return geometry
}

// =============================================================================
// FullShape Rendering
// =============================================================================

function renderFullShape(
  pipe: PipeData,
  config: VizConfig,
  prevPipe: PipeData | null,
  nextPipe: PipeData | null
): THREE.Mesh {
  const position = toThreePos(pipe.origin)
  const direction = toThreeDir(pipe.direction)
  
  // Compute miter normals for start and end caps
  const startMiterNormal = computeMiterNormal(
    direction,
    prevPipe ? toThreeDir(prevPipe.direction) : null
  )
  const endMiterNormal = computeMiterNormal(
    direction,
    nextPipe ? toThreeDir(nextPipe.direction) : null
  )
  
  // Build the mitered pipe geometry
  const geometry = buildMiteredPipeGeometry(
    pipe.shape,
    position,
    direction,
    pipe.length,
    startMiterNormal,
    endMiterNormal,
    pipe.shapeOrientation ?? 0
  )
  
  const material = new THREE.MeshStandardMaterial({
    color: pipe.color
  })
  const mesh = new THREE.Mesh(geometry, material)

  mesh.userData = { lineIndex: pipe.lineIndex }
  return mesh
}

// =============================================================================
// CenterLine Rendering
// =============================================================================

function renderCenterLine(
  pipe: PipeData,
  sphereRadius: number = 1,
  _lineWidth: number = 1
): THREE.Group {
  const group = new THREE.Group()

  const start = toThreePos(pipe.origin)
  const dir = toThreeDir(pipe.direction)
  const end = start.clone().add(dir.multiplyScalar(pipe.length))

  // Line (WebGL LineBasicMaterial.lineWidth is always 1 on most platforms)
  const points = [start, end]
  const lineGeo = new THREE.BufferGeometry().setFromPoints(points)
  const lineMat = new THREE.LineBasicMaterial({ color: pipe.color, linewidth: _lineWidth })
  group.add(new THREE.Line(lineGeo, lineMat))

  // Origin sphere
  const sphereGeo = new THREE.SphereGeometry(sphereRadius, 16, 16)
  const sphereMat = new THREE.MeshStandardMaterial({ color: pipe.color })
  const sphere = new THREE.Mesh(sphereGeo, sphereMat)
  sphere.position.copy(start)
  group.add(sphere)

  return group
}

// =============================================================================
// Enhanced ViewHelper with Axis Labels
// =============================================================================

/**
 * Enhanced ViewHelper that adds axis labels using HTML overlay.
 * Labels dynamically position themselves based on the camera orientation.
 */
class LabeledViewHelper {
  private viewHelper: ViewHelper
  private camera: THREE.PerspectiveCamera
  private controls: OrbitControls
  private domElement: HTMLElement
  private labelContainer: HTMLDivElement
  private labels: Map<string, { element: HTMLDivElement; axis: THREE.Vector3 }>
  private allAxes: THREE.Vector3[] // All 6 axes for click detection (positive + negative)
  private animating: boolean = false
  private animationTarget: { position: THREE.Vector3; up: THREE.Vector3 } | null = null
  private animationStart: { position: THREE.Vector3; up: THREE.Vector3 } | null = null
  private animationProgress: number = 0
  private axisLabels: { rear: string; up: string; right: string }
  
  constructor(
    camera: THREE.PerspectiveCamera,
    controls: OrbitControls,
    domElement: HTMLElement,
    axisLabels: { rear: string; up: string; right: string } = { rear: 'rear', up: 'up', right: 'right' }
  ) {
    this.axisLabels = axisLabels
    this.viewHelper = new ViewHelper(camera, domElement)
    this.camera = camera
    this.controls = controls
    this.domElement = domElement
    this.labels = new Map()
    this.allAxes = [
      new THREE.Vector3(1, 0, 0),   // +X (rear)
      new THREE.Vector3(0, 1, 0),   // +Y (up)
      new THREE.Vector3(0, 0, 1),   // +Z (right)
      new THREE.Vector3(-1, 0, 0),  // -X (front)
      new THREE.Vector3(0, -1, 0),  // -Y (bottom)
      new THREE.Vector3(0, 0, -1),  // -Z (left)
    ]
    
    // Create label container overlay positioned over the ViewHelper (bottom-right of canvas)
    this.labelContainer = document.createElement('div')
    this.labelContainer.style.position = 'absolute'
    this.labelContainer.style.width = '128px'
    this.labelContainer.style.height = '128px'
    this.labelContainer.style.pointerEvents = 'auto'
    this.labelContainer.style.userSelect = 'none'
    this.labelContainer.style.zIndex = '1000'
    
    // Append to the canvas's parent and ensure it has relative positioning
    const parent = domElement.parentElement!
    if (getComputedStyle(parent).position === 'static') {
      parent.style.position = 'relative'
    }
    parent.appendChild(this.labelContainer)
    
    // Distinguish click vs drag on gizmo:
    // - Click (no movement): snap to closest axis
    // - Drag: forward events to canvas for OrbitControls rotation
    this.labelContainer.style.cursor = 'grab'
    let pointerDownPos: { x: number; y: number } | null = null
    let isDragging = false
    
    this.labelContainer.addEventListener('pointerdown', (e: PointerEvent) => {
      pointerDownPos = { x: e.clientX, y: e.clientY }
      isDragging = false
    })
    
    this.labelContainer.addEventListener('pointermove', (e: PointerEvent) => {
      if (!pointerDownPos) return
      const dx = e.clientX - pointerDownPos.x
      const dy = e.clientY - pointerDownPos.y
      if (Math.sqrt(dx * dx + dy * dy) > 3) {
        if (!isDragging) {
          isDragging = true
          // Temporarily disable pointer events so canvas receives the drag
          this.labelContainer.style.pointerEvents = 'none'
        }
      }
    })
    
    this.labelContainer.addEventListener('pointerup', (e: PointerEvent) => {
      if (!isDragging && pointerDownPos) {
        // It was a click, not a drag - detect axis
        this.handleContainerClick(e)
      }
      // Re-enable pointer events
      this.labelContainer.style.pointerEvents = 'auto'
      pointerDownPos = null
      isDragging = false
    })
    
    // Also re-enable if pointer leaves the container during drag
    this.labelContainer.addEventListener('pointerleave', () => {
      if (isDragging) {
        this.labelContainer.style.pointerEvents = 'none'
      }
    })
    
    // Re-enable pointer events when mouse enters (after drag ended on canvas)
    this.labelContainer.addEventListener('pointerenter', () => {
      this.labelContainer.style.pointerEvents = 'auto'
      pointerDownPos = null
      isDragging = false
    })
    
    // Add labels to the container
    this.setupLabels()
  }
  
  private setupLabels(): void {
    // Define axis directions in Three.js space
    const axisData = [
      { text: this.axisLabels.rear,  color: '#ff0000', axis: new THREE.Vector3(1, 0, 0) },  // X axis (red)   = construction Rear
      { text: this.axisLabels.up,    color: '#00ff00', axis: new THREE.Vector3(0, 1, 0) },  // Y axis (green) = construction Up
      { text: this.axisLabels.right, color: '#0000ff', axis: new THREE.Vector3(0, 0, 1) }   // Z axis (blue)  = construction Right
    ]
    
    axisData.forEach(({ text, color, axis }) => {
      const div = document.createElement('div')
      div.textContent = text
      div.style.position = 'absolute'
      div.style.left = '64px' // Initial position at center
      div.style.top = '64px'
      div.style.color = color
      div.style.fontFamily = 'Roboto, Arial, sans-serif'
      div.style.fontSize = '12px'
      div.style.fontWeight = 'bold'
      div.style.padding = '0'
      div.style.background = 'none'
      div.style.pointerEvents = 'none'
      div.style.userSelect = 'none'
      div.style.whiteSpace = 'nowrap'
      div.style.zIndex = '1001'
      
      this.labelContainer.appendChild(div)
      this.labels.set(text, { element: div, axis: axis.clone() })
    })
  }
  
  private animateToAxis(axis: THREE.Vector3): void {
    if (this.animating) return
    
    const target = this.controls.target.clone()
    const distance = this.camera.position.distanceTo(target)
    
    // Position camera along the axis direction, looking at the orbit target
    const newPosition = target.clone().add(axis.clone().multiplyScalar(distance))
    
    // Determine up vector (avoid parallel up and look direction)
    let newUp = new THREE.Vector3(0, 1, 0)
    if (Math.abs(axis.y) > 0.9) {
      // Looking along Y axis: use Z as up
      newUp = new THREE.Vector3(0, 0, axis.y > 0 ? -1 : 1)
    }
    
    this.animationStart = {
      position: this.camera.position.clone(),
      up: this.camera.up.clone()
    }
    this.animationTarget = { position: newPosition, up: newUp }
    this.animationProgress = 0
    this.animating = true
  }
  
  /** Call in the render loop to advance any active animation */
  update(delta: number): void {
    if (!this.animating || !this.animationStart || !this.animationTarget) return
    
    this.animationProgress += delta * 3 // Complete in ~0.33s
    const t = Math.min(this.animationProgress, 1)
    // Smooth ease-in-out
    const ease = t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2
    
    this.camera.position.lerpVectors(this.animationStart.position, this.animationTarget.position, ease)
    this.camera.up.lerpVectors(this.animationStart.up, this.animationTarget.up, ease).normalize()
    this.camera.lookAt(this.controls.target)
    this.controls.update()
    
    if (t >= 1) {
      this.animating = false
      this.animationStart = null
      this.animationTarget = null
    }
  }
  
  /** Handle click on the label container overlay - coordinates relative to container */
  private handleContainerClick(event: MouseEvent): void {
    const containerRect = this.labelContainer.getBoundingClientRect()
    const clickX = event.clientX - containerRect.left
    const clickY = event.clientY - containerRect.top
    const size = 128
    
    this.findAndAnimateClosestAxis(clickX, clickY, size)
  }
  
  /** Handle click from the canvas - check if within gizmo area */
  handleClick(event: MouseEvent): void {
    const canvas = this.domElement as HTMLCanvasElement
    const canvasRect = canvas.getBoundingClientRect()
    
    const gizmoLeft = canvasRect.right - 128
    const gizmoTop = canvasRect.bottom - 128
    const clickX = event.clientX - gizmoLeft
    const clickY = event.clientY - gizmoTop
    
    if (clickX < 0 || clickX > 128 || clickY < 0 || clickY > 128) return
    
    this.findAndAnimateClosestAxis(clickX, clickY, 128)
  }
  
  /** Compute minimum distance from point to a line segment */
  private distToSegment(px: number, py: number, ax: number, ay: number, bx: number, by: number): number {
    const dx = bx - ax
    const dy = by - ay
    const lenSq = dx * dx + dy * dy
    if (lenSq === 0) return Math.sqrt((px - ax) ** 2 + (py - ay) ** 2)
    let t = ((px - ax) * dx + (py - ay) * dy) / lenSq
    t = Math.max(0, Math.min(1, t))
    const projX = ax + t * dx
    const projY = ay + t * dy
    return Math.sqrt((px - projX) ** 2 + (py - projY) ** 2)
  }
  
  private findAndAnimateClosestAxis(clickX: number, clickY: number, size: number): void {
    const centerPx = size / 2
    const radiusPx = size * 48 / 128
    let closestAxis: THREE.Vector3 | null = null
    let closestDist = size * 12 / 128 // 12px threshold at 128px scale (distance to line)
    
    const quaternion = this.camera.quaternion
    const cameraRight = new THREE.Vector3(1, 0, 0).applyQuaternion(quaternion)
    const cameraUp = new THREE.Vector3(0, 1, 0).applyQuaternion(quaternion)
    
    for (const axis of this.allAxes) {
      const x = axis.dot(cameraRight)
      const y = -axis.dot(cameraUp)
      const tipX = centerPx + x * radiusPx
      const tipY = centerPx + y * radiusPx
      
      const dist = this.distToSegment(clickX, clickY, centerPx, centerPx, tipX, tipY)
      if (dist < closestDist) {
        closestDist = dist
        closestAxis = axis
      }
    }
    
    if (closestAxis) {
      this.animateToAxis(closestAxis)
    }
  }
  
  render(renderer: THREE.WebGLRenderer): void {
    // Render the standard ViewHelper gizmo
    this.viewHelper.render(renderer)

    // Position the label container to match the ViewHelper's bottom-right viewport
    // ViewHelper renders at bottom-right: x = canvasWidth - 128, y = 0 (WebGL coords)
    // In CSS coords relative to canvas parent, we need the canvas offset within its parent
    const canvas = this.domElement as HTMLCanvasElement
    const parent = canvas.parentElement
    // Guard: canvas may not be in the DOM yet (e.g. first frames before Laminar mounts it)
    if (!parent) return
    const parentRect = parent.getBoundingClientRect()
    const canvasRect = canvas.getBoundingClientRect()
    
    // Calculate canvas position relative to parent
    const canvasRight = canvasRect.right - parentRect.left
    const canvasBottom = canvasRect.bottom - parentRect.top
    
    // Position label container at the bottom-right corner of the canvas
    this.labelContainer.style.left = `${canvasRight - 128}px`
    this.labelContainer.style.top = `${canvasBottom - 128}px`
    
    // Update label positions based on camera orientation
    this.updateLabelPositions()
  }
  
  private updateLabelPositions(): void {
    // Center of the ViewHelper gizmo (center of the 128x128 container)
    const center = { x: 64, y: 64 }
    const radius = 58 // Distance from center to place labels (beyond axis arrow tips)
    
    // For each axis, project it onto the view plane using camera orientation
    this.labels.forEach(({ element, axis }, name) => {
      // Create camera's local coordinate system vectors
      const cameraRight = new THREE.Vector3()
      const cameraUp = new THREE.Vector3()
      const cameraForward = new THREE.Vector3()
      
      // Extract camera's world orientation
      // Camera looks down -Z in local space, with +Y up and +X right
      const quaternion = this.camera.quaternion
      cameraRight.set(1, 0, 0).applyQuaternion(quaternion)
      cameraUp.set(0, 1, 0).applyQuaternion(quaternion)
      cameraForward.set(0, 0, -1).applyQuaternion(quaternion)
      
      // Project world axis onto camera's screen plane (right/up)
      const x = axis.dot(cameraRight)
      const y = -axis.dot(cameraUp) // Negative because screen Y goes down
      const z = axis.dot(cameraForward)
      
      // Scale directly by radius (don't normalize, so labels follow actual axis projection)
      const labelX = center.x + x * radius
      const labelY = center.y + y * radius
      
      // Position the label
      element.style.left = `${labelX}px`
      element.style.top = `${labelY}px`
      element.style.transform = 'translate(-50%, -50%)'
      
      // Fade labels that are facing away from camera (negative z = pointing toward viewer)
      element.style.opacity = z < 0 ? '1' : '0.4'
    })
  }
  
  dispose(): void {
    // Clean up label container
    this.labelContainer.remove()
  }
}

// =============================================================================
// Main Export Function
// =============================================================================

export function initFilaireViz(
  container: HTMLElement,
  pipeGroups: PipeGroup[],
  config: VizConfig,
  onPipeClick?: (lineIndex: number) => void,
  onPipeHover?: (lineIndex: number) => void
): FilaireVizHandle {
  // ---------------------------------------------------------------------------
  // Scene Setup
  // ---------------------------------------------------------------------------
  // Ensure container has relative positioning for watermark placement
  container.style.position = 'relative'

  // Measure container size, fall back to config values, fall back to defaults
  const containerWidth = container.clientWidth || config.canvasWidth || 750
  const containerHeight = container.clientHeight || config.canvasHeight || 600

  const renderer = new THREE.WebGLRenderer({ antialias: true })
  renderer.setSize(containerWidth, containerHeight)
  renderer.setClearColor(new THREE.Color(config.backgroundColor))
  container.appendChild(renderer.domElement)

  // ---------------------------------------------------------------------------
  // Watermark
  // ---------------------------------------------------------------------------
  if (config.watermark) {
    const watermarkDiv = document.createElement('div')
    watermarkDiv.textContent = config.watermark
    watermarkDiv.style.position = 'absolute'
    watermarkDiv.style.bottom = '20px'
    watermarkDiv.style.left = '50%'
    watermarkDiv.style.transform = 'translateX(-50%)'
    watermarkDiv.style.fontFamily = 'Roboto, sans-serif'
    watermarkDiv.style.fontSize = '12px'
    watermarkDiv.style.color = 'rgba(0, 0, 0, 0.4)'
    watermarkDiv.style.pointerEvents = 'none'
    watermarkDiv.style.userSelect = 'none'
    container.appendChild(watermarkDiv)
  }

  const scene = new THREE.Scene()

  const aspectRatio = containerWidth / containerHeight
  const camera = new THREE.PerspectiveCamera(60, aspectRatio, 0.1, 10000)

  const controls = new OrbitControls(camera, renderer.domElement)

  // ---------------------------------------------------------------------------
  // Lighting
  // ---------------------------------------------------------------------------
  // Hemisphere light: soft sky/ground gradient for ambient fill
  const hemiLight = new THREE.HemisphereLight(0xddeeff, 0x665544, 0.6)
  scene.add(hemiLight)

  // Key directional light for primary shadows/highlights
  const dirLight = new THREE.DirectionalLight(0xffffff, 0.8)
  dirLight.position.set(50, 100, 50)
  scene.add(dirLight)

  // Fill directional light from opposite side (dimmer) for secondary shading
  const fillLight = new THREE.DirectionalLight(0xffffff, 0.3)
  fillLight.position.set(-30, 40, -50)
  scene.add(fillLight)

  // ---------------------------------------------------------------------------
  // Render Pipes
  // ---------------------------------------------------------------------------
  // Track all pipe meshes for raycasting
  const pipeMeshes: THREE.Mesh[] = []
  let disposed = false

  if (config.displayType === 'FullShape' || config.displayType === 'Mixed') {
    const isMixed = config.displayType === 'Mixed'
    const shapeOpacity = isMixed ? (config.mixedShapeOpacity ?? 0.3) : 1.0

    for (const group of pipeGroups) {
      const pipes = group.pipes
      for (let i = 0; i < pipes.length; i++) {
        const pipe = pipes[i]
        const prevPipe = i > 0 ? pipes[i - 1] : null
        const nextPipe = i < pipes.length - 1 ? pipes[i + 1] : null
        const mesh = renderFullShape(pipe, config, prevPipe, nextPipe)

        if (isMixed) {
          const mat = mesh.material as THREE.MeshStandardMaterial
          mat.transparent = true
          mat.opacity = shapeOpacity
          mat.depthWrite = false
        }

        scene.add(mesh)
        pipeMeshes.push(mesh)

        // Add visible edges using a darker shade of the pipe color
        const edgeColor = new THREE.Color(pipe.color).multiplyScalar(0.3)
        const edges = new THREE.EdgesGeometry(mesh.geometry, 15)
        const edgeLine = new THREE.LineSegments(
          edges,
          new THREE.LineBasicMaterial({ color: edgeColor })
        )
        scene.add(edgeLine)
      }
    }
  }

  // Track Line objects for CenterLine click raycasting
  const centerLineObjects: THREE.Line[] = []

  if (config.displayType === 'CenterLine' || config.displayType === 'Mixed') {
    const sphereRadius = config.centerLineSphereRadius ?? 1
    const lineWidth = config.centerLineStrokeWidth ?? 1

    for (const group of pipeGroups) {
      for (const pipe of group.pipes) {
        const clGroup = renderCenterLine(pipe, sphereRadius, lineWidth)
        // Tag Line objects with lineIndex for raycasting (not spheres)
        clGroup.traverse((child) => {
          if (child instanceof THREE.Line) {
            child.userData = { lineIndex: pipe.lineIndex }
            centerLineObjects.push(child)
          }
        })
        scene.add(clGroup)
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Camera Positioning
  // ---------------------------------------------------------------------------
  const box = new THREE.Box3().setFromObject(scene)
  const center = box.getCenter(new THREE.Vector3())
  const size = box.getSize(new THREE.Vector3())
  const maxDim = Math.max(size.x, size.y, size.z)
  const viewPadding = config.viewPadding ?? 1.5
  const distance = maxDim * viewPadding || 100 // Fallback if no objects

  // Always compute the default camera position (azimuth=225°, elevation=45°)
  const azimuth = Math.PI + Math.PI / 4  // 5τ/8 = π + π/4
  const elevation = Math.PI / 4           // τ/8
  const defaultCameraPos = new THREE.Vector3(
    center.x + distance * Math.cos(elevation) * Math.sin(azimuth),
    center.y + distance * Math.sin(elevation),
    center.z + distance * Math.cos(elevation) * Math.cos(azimuth)
  )
  const defaultCameraUp = new THREE.Vector3(0, 1, 0)
  const defaultTarget = center.clone()

  // Save default state for reset button (before any persisted override)
  const initialCameraPos = defaultCameraPos.clone()
  const initialCameraUp = defaultCameraUp.clone()
  const initialTarget = defaultTarget.clone()

  // Restore camera state if provided (e.g. from view mode toggle), otherwise use default
  if (config._cameraState) {
    const cs = config._cameraState
    camera.position.set(cs.position[0], cs.position[1], cs.position[2])
    camera.up.set(cs.up[0], cs.up[1], cs.up[2])
    controls.target.set(cs.target[0], cs.target[1], cs.target[2])
    camera.lookAt(controls.target)
    controls.update()
  } else {
    camera.position.copy(defaultCameraPos)
    camera.up.copy(defaultCameraUp)
    camera.lookAt(defaultTarget)
    controls.target.copy(defaultTarget)
    controls.update()
  }

  // ---------------------------------------------------------------------------
  // ViewHelper (CAD-style orientation gizmo with labels)
  // ---------------------------------------------------------------------------
  const axisLabels = {
    rear:  config.labelAxisRear  ?? 'rear',
    up:    config.labelAxisUp    ?? 'up',
    right: config.labelAxisRight ?? 'right',
  }
  const viewHelper = new LabeledViewHelper(camera, controls, renderer.domElement, axisLabels)
  const clock = new THREE.Clock()

  // Handle click events for ViewHelper axis snapping
  renderer.domElement.addEventListener('click', (event: MouseEvent) => {
    viewHelper.handleClick(event)
  })

  // ---------------------------------------------------------------------------
  // Reset View Button (positioned above the gizmo)
  // ---------------------------------------------------------------------------
  const resetBtn = document.createElement('button')
  resetBtn.textContent = config.labelResetView ?? 'reset view'
  resetBtn.style.position = 'absolute'
  resetBtn.style.zIndex = '1001'
  resetBtn.style.padding = '3px 8px'
  resetBtn.style.fontSize = '12px'
  resetBtn.style.cursor = 'pointer'
  resetBtn.style.background = '#ffffff'
  resetBtn.style.border = '1px solid #bbb'
  resetBtn.style.borderRadius = '3px'
  resetBtn.style.color = '#333'
  resetBtn.style.pointerEvents = 'auto'

  const canvasParent = renderer.domElement.parentElement!
  if (getComputedStyle(canvasParent).position === 'static') {
    canvasParent.style.position = 'relative'
  }
  canvasParent.appendChild(resetBtn)

  // Position the reset button above the gizmo (updated on resize)
  function positionResetButton(): void {
    const rect = renderer.domElement.getBoundingClientRect()
    const parentRect = canvasParent.getBoundingClientRect()
    const canvasRight = rect.right - parentRect.left
    const canvasBottom = rect.bottom - parentRect.top
    // Gizmo is 128×128 at bottom-right; place button above it, right-aligned
    // Center above the 128px gizmo area at bottom-right of canvas
    const gizmoCenter = canvasRight - 64 // center of the 128px gizmo
    resetBtn.style.left = `${gizmoCenter}px`
    resetBtn.style.transform = 'translateX(-50%)'
    resetBtn.style.top = `${canvasBottom - 128 - 28}px`
    resetBtn.style.whiteSpace = 'nowrap'
  }
  // Position buttons after a delay to ensure DOM layout is complete
  requestAnimationFrame(() => {
    positionResetButton()
  })

  // ---------------------------------------------------------------------------
  // View Mode Button (positioned at top-right, aligned with reset button)
  // ---------------------------------------------------------------------------
  const viewModeBtn = document.createElement('button')
  viewModeBtn.textContent = config.labelViewMode ?? 'view mode'
  viewModeBtn.style.position = 'absolute'
  viewModeBtn.style.zIndex = '1001'
  viewModeBtn.style.padding = '3px 8px'
  viewModeBtn.style.fontSize = '12px'
  viewModeBtn.style.cursor = 'pointer'
  viewModeBtn.style.background = '#ffffff'
  viewModeBtn.style.border = '1px solid #bbb'
  viewModeBtn.style.borderRadius = '3px'
  viewModeBtn.style.color = '#333'
  viewModeBtn.style.pointerEvents = 'auto'
  viewModeBtn.style.whiteSpace = 'nowrap'
  canvasParent.appendChild(viewModeBtn)

  function positionViewModeButton(): void {
    const rect = renderer.domElement.getBoundingClientRect()
    const parentRect = canvasParent.getBoundingClientRect()
    const canvasRight = rect.right - parentRect.left
    const canvasTop = rect.top - parentRect.top
    const gizmoCenter = canvasRight - 64
    viewModeBtn.style.left = `${gizmoCenter}px`
    viewModeBtn.style.transform = 'translateX(-50%)'
    viewModeBtn.style.top = `${canvasTop + 8}px`
  }
  // Position buttons after a delay to ensure DOM layout is complete
  requestAnimationFrame(() => {
    positionViewModeButton()
  })

  viewModeBtn.addEventListener('click', () => {
    const cycle: Record<VizConfig['displayType'], VizConfig['displayType']> = {
      CenterLine: 'FullShape',
      FullShape: 'Mixed',
      Mixed: 'CenterLine'
    }
    const newDisplayType = cycle[config.displayType]
    // Capture current camera state to preserve rotation across re-init
    const cameraState: VizConfig['_cameraState'] = {
      position: [camera.position.x, camera.position.y, camera.position.z],
      up: [camera.up.x, camera.up.y, camera.up.z],
      target: [controls.target.x, controls.target.y, controls.target.z]
    }
    const newConfig = { ...config, displayType: newDisplayType, _cameraState: cameraState }
    // Stop current animation loop and clear container
    disposed = true
    resizeObserver.disconnect()
    renderer.dispose()
    viewHelper.dispose()
    container.innerHTML = ''
    initFilaireViz(container, pipeGroups, newConfig, onPipeClick, onPipeHover)
  })

  resetBtn.addEventListener('click', () => {
    camera.position.copy(initialCameraPos)
    camera.up.copy(initialCameraUp)
    controls.target.copy(initialTarget)
    camera.lookAt(initialTarget)
    controls.update()
  })

  // ---------------------------------------------------------------------------
  // Raycaster Setup (Hover & Click)
  // ---------------------------------------------------------------------------
  const raycaster = new THREE.Raycaster()
  // Set line intersection threshold (in world units) for easier clicking
  raycaster.params.Line = { threshold: 3 }
  const pointer = new THREE.Vector2()
  let hoveredMesh: THREE.Mesh | null = null
  const originalColors = new Map<THREE.Mesh, THREE.Color>()

  // Hover highlight
  renderer.domElement.addEventListener('pointermove', (event: PointerEvent) => {
    const rect = renderer.domElement.getBoundingClientRect()
    pointer.x = ((event.clientX - rect.left) / rect.width) * 2 - 1
    pointer.y = -((event.clientY - rect.top) / rect.height) * 2 + 1

    raycaster.setFromCamera(pointer, camera)

    // Combine pipe meshes and centerline objects for raycasting
    const allClickable: THREE.Object3D[] = [...pipeMeshes, ...centerLineObjects]
    const intersects = raycaster.intersectObjects(allClickable, false)

    // Restore previous hover color
    if (hoveredMesh) {
      const orig = originalColors.get(hoveredMesh)
      if (orig) (hoveredMesh.material as THREE.MeshStandardMaterial).color.copy(orig)
      hoveredMesh = null
      renderer.domElement.style.cursor = 'default'
    }

    if (intersects.length > 0) {
      const obj = intersects[0].object
      if (obj instanceof THREE.Mesh) {
        if (!originalColors.has(obj)) {
          originalColors.set(obj, (obj.material as THREE.MeshStandardMaterial).color.clone())
        }
        ;(obj.material as THREE.MeshStandardMaterial).color.set(config.hoverColor)
        renderer.domElement.style.cursor = 'pointer'
        hoveredMesh = obj
      } else {
        // Line object - just show pointer cursor
        renderer.domElement.style.cursor = 'pointer'
      }
      if (onPipeHover) onPipeHover(intersects[0].object.userData.lineIndex as number)
    } else {
      if (onPipeHover) onPipeHover(-1)
    }
  })

  // Click with drag discrimination
  let pointerDownPos = { x: 0, y: 0 }

  renderer.domElement.addEventListener('pointerdown', (event: PointerEvent) => {
    pointerDownPos = { x: event.clientX, y: event.clientY }
  })

  renderer.domElement.addEventListener('pointerup', (event: PointerEvent) => {
    const dx = event.clientX - pointerDownPos.x
    const dy = event.clientY - pointerDownPos.y
    if (Math.sqrt(dx * dx + dy * dy) < 3) {
      // It's a click, not a drag
      raycaster.setFromCamera(pointer, camera)
      const allClickable: THREE.Object3D[] = [...pipeMeshes, ...centerLineObjects]
      const intersects = raycaster.intersectObjects(allClickable, false)
      if (intersects.length > 0 && onPipeClick) {
        const lineIndex = intersects[0].object.userData.lineIndex as number
        onPipeClick(lineIndex)
      } else if (intersects.length === 0 && onPipeClick) {
        onPipeClick(-1)
      }
    }
  })

  // ---------------------------------------------------------------------------
  // Pipe Name Labels (HTML overlay, projected from 3D to 2D)
  // ---------------------------------------------------------------------------
  const nameLabels: Array<{ element: HTMLDivElement; worldPos: THREE.Vector3 }> = []
  const displayNameInModes = config.displayNameInModes ?? ['CenterLine']
  // Default visibility: on if displayName is true AND current mode is in displayNameInModes
  let annotationsVisible = (config.displayName ?? false) && displayNameInModes.includes(config.displayType)

  // Always create labels (so the toggle button can show them in any mode)
  {
    const nameOffset = config.nameVerticalOffset ?? 3
    const labelParent = renderer.domElement.parentElement!
    if (getComputedStyle(labelParent).position === 'static') {
      labelParent.style.position = 'relative'
    }

    for (const group of pipeGroups) {
      for (const pipe of group.pipes) {
        if (!pipe.name) continue

        // Compute midpoint of pipe in Three.js coords
        const start = toThreePos(pipe.origin)
        const dir = toThreeDir(pipe.direction)
        const mid = start.clone().add(dir.clone().multiplyScalar(pipe.length / 2))

        // Compute an orthogonal offset vector that is axis-aligned.
        const candidates: THREE.Vector3[] = [
          new THREE.Vector3(0, 1, 0),  // up
          new THREE.Vector3(0, -1, 0), // down
          new THREE.Vector3(0, 0, 1),  // right
          new THREE.Vector3(0, 0, -1), // left
        ]
        let bestAxis = candidates[0]
        let bestDot = Math.abs(dir.dot(candidates[0]))
        for (let c = 1; c < candidates.length; c++) {
          const d = Math.abs(dir.dot(candidates[c]))
          if (d < bestDot) {
            bestDot = d
            bestAxis = candidates[c]
          }
        }
        mid.add(bestAxis.clone().multiplyScalar(nameOffset))

        const label = document.createElement('div')
        label.textContent = pipe.displayName ?? pipe.name
        label.style.position = 'absolute'
        label.style.pointerEvents = 'auto'
        label.style.userSelect = 'none'
        label.style.fontFamily = 'Roboto, Arial, sans-serif'
        label.style.fontSize = '12px'
        label.style.fontWeight = 'bold'
        label.style.color = pipe.color
        label.style.whiteSpace = 'nowrap'
        label.style.transform = 'translate(-50%, -50%)'
        label.style.zIndex = '999'
        label.style.cursor = 'pointer'
        label.style.background = '#f5f5f5'
        label.style.border = '1px solid #bbb'
        label.style.borderRadius = '3px'
        label.style.padding = '3px 8px'

        const lineIndex = pipe.lineIndex
        label.addEventListener('click', () => {
          if (onPipeClick) onPipeClick(lineIndex)
        })
        label.addEventListener('pointerenter', () => {
          if (onPipeHover) onPipeHover(lineIndex)
        })
        label.addEventListener('pointerleave', () => {
          if (onPipeHover) onPipeHover(-1)
        })

        labelParent.appendChild(label)
        nameLabels.push({ element: label, worldPos: mid })
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Annotations Toggle Button
  // ---------------------------------------------------------------------------
  const annotationsBtn = document.createElement('button')
  annotationsBtn.textContent = config.labelAnnotations ?? 'annotations'
  annotationsBtn.style.position = 'absolute'
  annotationsBtn.style.zIndex = '1001'
  annotationsBtn.style.padding = '3px 8px'
  annotationsBtn.style.fontSize = '12px'
  annotationsBtn.style.cursor = 'pointer'
  annotationsBtn.style.background = '#ffffff'
  annotationsBtn.style.border = '1px solid #bbb'
  annotationsBtn.style.borderRadius = '3px'
  annotationsBtn.style.color = '#333'
  annotationsBtn.style.pointerEvents = 'auto'
  annotationsBtn.style.whiteSpace = 'nowrap'
  canvasParent.appendChild(annotationsBtn)

  function positionAnnotationsButton(): void {
    const rect = renderer.domElement.getBoundingClientRect()
    const parentRect = canvasParent.getBoundingClientRect()
    const canvasRight = rect.right - parentRect.left
    const canvasTop = rect.top - parentRect.top
    const gizmoCenter = canvasRight - 64
    annotationsBtn.style.left = `${gizmoCenter}px`
    annotationsBtn.style.transform = 'translateX(-50%)'
    // Position below the view mode button (viewMode is at canvasTop+8, ~24px tall)
    annotationsBtn.style.top = `${canvasTop + 36}px`
  }
  // Position buttons after a delay to ensure DOM layout is complete
  requestAnimationFrame(() => {
    positionAnnotationsButton()
  })

  annotationsBtn.addEventListener('click', () => {
    annotationsVisible = !annotationsVisible
  })

  function updateNameLabels(): void {
    if (nameLabels.length === 0) return

    const canvas = renderer.domElement
    // Guard: canvas may not be in the DOM yet
    if (!canvas.parentElement) return
    const canvasRect = canvas.getBoundingClientRect()
    const parentRect = canvas.parentElement.getBoundingClientRect()
    const offsetX = canvasRect.left - parentRect.left
    const offsetY = canvasRect.top - parentRect.top

    for (const { element, worldPos } of nameLabels) {
      if (!annotationsVisible) {
        element.style.display = 'none'
        continue
      }

      const projected = worldPos.clone().project(camera)
      const x = (projected.x * 0.5 + 0.5) * canvasRect.width + offsetX
      const y = (-projected.y * 0.5 + 0.5) * canvasRect.height + offsetY

      if (projected.z > 1) {
        element.style.display = 'none'
      } else {
        element.style.display = ''
        element.style.left = `${x}px`
        element.style.top = `${y}px`
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Animation Loop
  // ---------------------------------------------------------------------------
  function animate(): void {
    if (disposed) return
    requestAnimationFrame(animate)
    const delta = clock.getDelta()
    controls.update()

    // Restore full viewport for main render
    const size = renderer.getSize(new THREE.Vector2())
    renderer.setViewport(0, 0, size.x, size.y)
    renderer.setScissor(0, 0, size.x, size.y)
    renderer.setScissorTest(false)

    // Render main scene first (full viewport)
    renderer.autoClear = true
    renderer.render(scene, camera)

    // Render ViewHelper (uses its own small viewport/scissor)
    renderer.autoClear = false
    viewHelper.update(delta)
    viewHelper.render(renderer)
    renderer.autoClear = true

    // Update projected name labels
    updateNameLabels()
  }
  animate()

  // ---------------------------------------------------------------------------
  // ResizeObserver: react to container size changes
  // ---------------------------------------------------------------------------
  const resizeObserver = new ResizeObserver((entries) => {
    for (const entry of entries) {
      const { width, height } = entry.contentRect
      if (width > 0 && height > 0) {
        renderer.setSize(width, height)
        camera.aspect = width / height
        camera.updateProjectionMatrix()
        positionResetButton()
        positionViewModeButton()
        positionAnnotationsButton()
      }
    }
  })
  resizeObserver.observe(container)

  // Return handle for lifecycle management
  return {
    dispose() {
      disposed = true
      resizeObserver.disconnect()
      renderer.dispose()
      viewHelper.dispose()
      container.innerHTML = ''
    },
    getCameraState() {
      return {
        position: [camera.position.x, camera.position.y, camera.position.z],
        up: [camera.up.x, camera.up.y, camera.up.z],
        target: [controls.target.x, controls.target.y, controls.target.z]
      }
    }
  }
}
