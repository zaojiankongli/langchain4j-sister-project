/**
 * Live2D 动作管理器
 * 处理多种触发源（点击、随机、定时）并避免动作冲突
 */

// 动作优先级定义
const MotionPriority = {
  NONE: 0,
  IDLE: 1,
  NORMAL: 2,
  SPECIAL: 3,
  FORCE: 4
}

// 动作组配置
const MotionGroups = {
  IDLE: 'idle',
  TOUCH_HEAD: 'touch_head',
  TOUCH_BODY: 'touch_body',
  TOUCH_SPECIAL: 'touch_special',
  LOGIN: 'login',
  MAIL: 'mail',
  HOME: 'home',
  COMPLETE: 'complete',
  MISSION: 'mission',
  MISSION_COMPLETE: 'mission_complete',
  WEDDING: 'wedding',
  MAIN_1: 'main_1',
  MAIN_2: 'main_2',
  MAIN_3: 'main_3'
}

// 动作配置
const MotionConfig = {
  idleMotions: [MotionGroups.IDLE, MotionGroups.MAIN_1, MotionGroups.MAIN_2, MotionGroups.MAIN_3],
  touchMotions: {
    head: MotionGroups.TOUCH_HEAD,
    body: MotionGroups.TOUCH_BODY,
    special: MotionGroups.TOUCH_SPECIAL
  },
  touchPriority: MotionPriority.NORMAL,
  idlePriority: MotionPriority.IDLE,
  idleIntervalMin: 5000,
  idleIntervalMax: 15000,
  minPlayTime: 1000
}

export class Live2DMotionManager {
  constructor(oml2dInstance) {
    this.oml2d = oml2dInstance
    this.currentPriority = MotionPriority.NONE
    this.isPlaying = false
    this.currentMotion = null
    this.lastPlayTime = 0
    this.idleTimer = null
    this.isDestroyed = false

    // 绑定上下文
    this._startIdle = this._startIdle.bind(this)
  }

  /**
   * 检查是否可以播放新动作
   * @param {number} priority 新动作优先级
   * @returns {boolean}
   */
  canPlayMotion(priority) {
    if (this.isDestroyed) return false

    // 强制动作永远可以播放
    if (priority === MotionPriority.FORCE) return true

    // 如果当前没有播放任何动作，可以播放
    if (!this.isPlaying) return true

    // 只有更高优先级的动作才能打断当前动作
    return priority > this.currentPriority
  }

  /**
   * 检查距离上次播放是否足够时间
   * @returns {boolean}
   */
  canInterrupt() {
    return Date.now() - this.lastPlayTime >= MotionConfig.minPlayTime
  }

  /**
   * 播放指定动作
   * @param {string} motionName 动作名称（不带 .motion3.json）
   * @param {number} priority 优先级
   * @param {object} options 额外选项
   * @returns {Promise<boolean>}
   */
  async playMotion(motionName, priority = MotionPriority.NORMAL, options = {}) {
    const { force = false, onStart, onEnd } = options

    // 检查是否可以播放
    if (!force && !this.canPlayMotion(priority)) {
      return false
    }

    if (!force && !this.canInterrupt()) {
      return false
    }

    try {
      // 停止当前空闲定时器
      this._stopIdle()

      this.isPlaying = true
      this.currentPriority = priority
      this.currentMotion = motionName
      this.lastPlayTime = Date.now()

      // 调用底层的动作播放（如果可用）
      await this._executeMotion(motionName)

      onStart?.()

      // 动作播放完成回调
      const motionDuration = MotionConfig.minPlayTime * 3
      setTimeout(() => {
        this._onMotionEnd(motionName, onEnd)
      }, motionDuration)

      return true
    } catch (error) {
      this._onMotionEnd(motionName, onEnd)
      return false
    }
  }

  /**
   * 内部执行动作
   * @private
   */
  async _executeMotion(motionName) {
    try {
      const live2dModel = this.oml2d?.models?.model
      if (!live2dModel) {
        return
      }

      const motionGroups = live2dModel?.internalModel?.motionManager?.motionGroups

      const groupKeys = Object.keys(motionGroups || {})

      const groupName = groupKeys[0] || ''

      const mm = live2dModel?.internalModel?.motionManager
      if (mm && typeof mm.startRandomMotion === 'function') {
        await mm.startRandomMotion(groupName)
        return
      }

      // 备用：使用 live2dModel.motion(groupName) 随机播放
      if (typeof live2dModel.motion === 'function') {
        await live2dModel.motion(groupName)
        return
      }

    } catch (e) {
    }
  }

  /**
   * 动作播放结束处理
   * @private
   */
  _onMotionEnd(motionName, callback) {
    if (this.currentMotion !== motionName) return

    this.isPlaying = false
    this.currentPriority = MotionPriority.NONE
    this.currentMotion = null

    callback?.()

    // 重新开始空闲检测
    if (!this.isDestroyed) {
      this._scheduleIdle()
    }
  }

  /**
   * 处理点击事件
   * @param {string} hitArea 点击区域 (head/body/special)
   */
  onTap(hitArea) {
    const motionName = MotionConfig.touchMotions[hitArea]
    if (!motionName) return

    this.playMotion(motionName, MotionConfig.touchPriority)
  }

  /**
   * 开始空闲动作循环
   */
  startIdle() {
    this._scheduleIdle()
  }

  /**
   * 停止空闲动作
   */
  stopIdle() {
    this._stopIdle()
  }

  /**
   * 调度空闲动作
   * @private
   */
  _scheduleIdle() {
    if (this.isDestroyed) return

    const delay = MotionConfig.idleIntervalMin +
      Math.random() * (MotionConfig.idleIntervalMax - MotionConfig.idleIntervalMin)

    this.idleTimer = setTimeout(() => {
      this._startIdle()
    }, delay)
  }

  /**
   * 执行空闲动作
   * @private
   */
  async _startIdle() {
    if (this.isDestroyed || this.isPlaying) {
      this._scheduleIdle()
      return
    }

    const idleMotions = MotionConfig.idleMotions
    const randomMotion = idleMotions[Math.floor(Math.random() * idleMotions.length)]

    await this.playMotion(randomMotion, MotionConfig.idlePriority, {
      onEnd: () => {
        if (!this.isDestroyed) {
          this._scheduleIdle()
        }
      }
    })
  }

  /**
   * 停止空闲定时器
   * @private
   */
  _stopIdle() {
    if (this.idleTimer) {
      clearTimeout(this.idleTimer)
      this.idleTimer = null
    }
  }

  /**
   * 销毁管理器
   */
  destroy() {
    this.isDestroyed = true
    this._stopIdle()
    this.oml2d = null
  }
}
