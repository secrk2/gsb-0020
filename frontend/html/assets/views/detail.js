/* 对象档案详情：实名二次确认、状态机流转、留痕时间线、轨迹回放 */
(function (global) {
  const Views = global.Views || (global.Views = {});

  // 与后端状态机保持一致的合法去向（后端为最终裁决，前端仅用于渲染按钮）
  const NEXT = {
    INTAKE: [{ to: 'SERVING', label: '办理入矫宣告', needReason: false }],
    SERVING: [
      { to: 'LEAVE', label: '批准请假外出', needReason: true },
      { to: 'ADMONISHED', label: '予以训诫', needReason: true, danger: true },
      { to: 'REIMPRISONED', label: '提请收监', needReason: true, danger: true },
      { to: 'RELEASED', label: '解除矫正', needReason: true },
    ],
    LEAVE: [
      { to: 'SERVING', label: '销假返所', needReason: false },
      { to: 'ADMONISHED', label: '逾假不归·训诫', needReason: true, danger: true },
    ],
    ADMONISHED: [
      { to: 'SERVING', label: '教育改正·恢复在矫', needReason: false },
      { to: 'REIMPRISONED', label: '情节严重·收监', needReason: true, danger: true },
    ],
    REIMPRISONED: [],
    RELEASED: [],
  };

  Views.detail = async function (root, id) {
    root.innerHTML = `<div class="skeleton">档案加载中…</div>`;

    let detail;
    try {
      detail = await Api.get('/objects/' + id);
    } catch (e) {
      if (e.code === 'FORBIDDEN' || e.status === 403) {
        root.innerHTML = forbiddenHtml(e.message);
        root.querySelector('#btn-back').onclick = () => { location.hash = '#/objects'; };
        return;
      }
      if (e.code === 'NOT_FOUND' || e.status === 404) {
        root.innerHTML = stateHtml('🧭', '档案不存在', e.message);
        return;
      }
      root.innerHTML = stateHtml('⚠️', '档案加载失败', e.message);
      return;
    }

    const o = detail.object;
    render();

    function render() {
      const actions = NEXT[o.status] || [];
      root.innerHTML = `
        <div class="page-head" style="display:flex;align-items:center;gap:12px;flex-wrap:wrap">
          <button class="btn sm" id="btn-back">← 返回列表</button>
          <h2 style="margin:0">${UI.esc(o.maskedName)}</h2>
          <span style="color:var(--ink-muted);font-size:13px">${UI.esc(o.correctionNo)}</span>
          ${UI.statusBadge(o.status)}
          <span style="flex:1"></span>
          <span style="font-size:12.5px;color:var(--ink-muted)">${UI.esc(o.officeName)}</span>
        </div>

        <div class="detail-grid">
          <div>
            <div class="card">
              <div class="card-title">📄 档案信息
                <span class="sub">实名默认脱敏隐藏</span>
              </div>
              <dl class="kv">
                <dt>真实姓名</dt>
                <dd>
                  <span class="name-secret">
                    <span class="secret" id="btn-reveal" title="查看需二次确认并填写理由">●●● 点击申请查看</span>
                    <span id="fullname-slot" style="font-weight:700;display:none"></span>
                  </span>
                </dd>
                <dt>罪名</dt><dd>${UI.esc(o.charge || '—')}</dd>
                <dt>证件尾号</dt><dd>${UI.esc(o.idCardTail || '—')}</dd>
                <dt>联系电话</dt><dd>${UI.esc(o.phone || '—')}</dd>
                <dt>矫正期限</dt><dd>${UI.esc(o.startDate || '—')} 至 ${UI.esc(o.endDate || '—')}</dd>
                <dt>规定报到</dt><dd>每${UI.WEEK_LABEL[o.reportDay] || '—'}</dd>
                <dt>今日报到</dt>
                <dd>${detail.checkedToday
                  ? '<span class="badge green">已报到</span>'
                  : '<span class="badge gray">未报到</span>'}</dd>
                <dt>最近定位</dt>
                <dd>${o.lastLocationAt
                  ? `${UI.fmtTzFull(o.lastLocationAt, o.timezone)}（${UI.esc(o.timezone)}） · ` +
                    (o.lastInsideFence ? '<span class="badge green">围栏内</span>' : '<span class="badge red">📍越界</span>')
                    + (o.lastForbidden ? ' <span class="badge red">禁区</span>' : '')
                  : '暂无'}</dd>
              </dl>
              <div id="audit-slot"></div>
            </div>

            <div class="card">
              <div class="card-title">🧭 定位轨迹
                <span class="sub">共 ${detail.trackCount} 个有效点（重复补传点不计入）</span>
              </div>
              <div id="tracks-slot"><div class="skeleton">轨迹加载中…</div></div>
            </div>
          </div>

          <div>
            <div class="card">
              <div class="card-title">🔄 状态流转</div>
              ${actions.length ? `
                <div style="font-size:13px;color:var(--ink-secondary);margin-bottom:6px">
                  当前「${UI.STATUS_LABEL[o.status]}」，可执行：
                </div>
                <div class="transition-pad">
                  ${actions.map((a) => `
                    <button class="btn ${a.danger ? 'danger' : 'primary'} sm" data-action="${a.to}"
                      data-need-reason="${a.needReason}">${a.label}</button>`).join('')}
                </div>` : `
                <div class="confirm-warn">该状态为终态（${UI.STATUS_LABEL[o.status]}），
                  状态机不允许任何回退或跳转。</div>`}
              <div id="transition-result" style="margin-top:12px"></div>
            </div>

            <div class="card">
              <div class="card-title">🧾 流转留痕</div>
              <div class="timeline">
                ${detail.transitions.length ? detail.transitions.map((t) => `
                  <div class="tl-item">
                    <div class="tl-line">
                      <b>${UI.esc(t.fromStatusLabel)}</b> → <b>${UI.esc(t.toStatusLabel)}</b>
                    </div>
                    <div class="tl-meta">
                      ${UI.esc(t.operatorName)} · ${UI.fmtTzFull(t.operatedAt, o.timezone)}
                      ${t.reason ? '· ' + UI.esc(t.reason) : ''}
                    </div>
                  </div>`).join('') : '<div style="color:var(--ink-muted);font-size:13px">暂无流转记录</div>'}
              </div>
            </div>

            <div class="card">
              <div class="card-title">🚨 违规与越界记录</div>
              <div class="violation-list">
                ${detail.violations.length ? detail.violations.map((v) => `
                  <div class="violation-item">
                    <span class="red-dot" style="margin-top:6px;${v.read ? 'background:var(--neutral)' : ''}"></span>
                    <div class="v-body">
                      <div class="v-detail"><span class="badge ${v.read ? 'gray' : 'red'}" style="margin-right:6px">${UI.esc(v.typeLabel)}${v.read ? '·已处置' : ''}</span>${UI.esc(v.detail)}</div>
                      <div class="v-meta">${UI.fmtTzFull(v.eventTime, o.timezone)}（${UI.esc(o.timezone)}）</div>
                    </div>
                  </div>`).join('')
                  : '<div style="color:var(--good);font-size:13px">暂无违规记录</div>'}
              </div>
            </div>

            <div class="card">
              <div class="card-title">⚖️ 违规处置案件
                <span class="sub">${detail.cases.length} 件</span>
              </div>
              <div class="violation-list">
                ${detail.cases.length ? detail.cases.slice(0, 6).map((c) => `
                  <div class="violation-item" data-case="${c.id}" style="cursor:pointer">
                    <span style="margin-top:2px">⚖️</span>
                    <div class="v-body">
                      <div class="v-detail"><b>${UI.esc(c.caseNo)}</b>
                        <span class="badge ${c.status === 'REGISTERED' ? 'red' : 'green'}" style="margin-left:6px">${UI.esc(c.statusLabel)}</span>
                        <span class="badge gray" style="margin-left:4px">${UI.esc(c.reasonTypeLabel)}</span></div>
                      <div class="v-meta">${UI.esc(c.registeredByName)} 登记 · ${UI.fmtTz(c.registeredAt, o.timezone)}
                        ${c.mergedEventCount > 1 ? ' · 合并预警 ' + c.mergedEventCount + ' 条' : ''}</div>
                    </div>
                  </div>`).join('')
                  : '<div style="color:var(--ink-muted);font-size:13px">暂无处置案件</div>'}
              </div>
            </div>

            <div class="card">
              <div class="card-title">📭 解除与评估</div>
              ${o.releaseCertificateNo ? `
                <div class="case-seal">
                  📜 <b>已解除社区矫正（永久标记）</b><br/>
                  解除证明书编号：<b>${UI.esc(o.releaseCertificateNo)}</b><br/>
                  解除时间：${UI.fmtTzFull(o.releasedMarkedAt, o.timezone)}（${UI.esc(o.timezone)}）<br/>
                  定位数据已停止实时更新；本档案按矫正编号长期可查。
                </div>` : ''}
              ${detail.releaseAssessment ? (() => {
                const a = detail.releaseAssessment;
                return `
                <div style="font-size:13px;line-height:1.9">
                  <div>评估报告：<b>${UI.esc(a.reportNo)}</b>
                    <span class="badge ${a.status === 'DONE' ? 'green' : a.status === 'REJECTED' ? 'red' : ''}" style="margin-left:6px">${UI.esc(a.statusLabel)}</span></div>
                  <div style="color:var(--ink-muted)">期满日 ${UI.esc(a.dueDate)} · 结论：${UI.esc(a.conclusionLabel)} · ${UI.esc(a.generatedByName)} 生成</div>
                  ${a.releaseCertificateNo ? `<div style="color:var(--good)">📜 ${UI.esc(a.releaseCertificateNo)}</div>` : ''}
                </div>
                <button class="btn sm primary" id="btn-release-link" style="margin-top:8px">查看评估流程</button>`;
              })() : `
                <div style="color:var(--ink-muted);font-size:13px;margin-bottom:8px">
                  矫正期满前须先生成解除评估报告，经审批通过后执行解除。</div>
                <button class="btn sm" id="btn-release-link">前往解除与评估</button>`}
            </div>
          </div>
        </div>`;

      root.querySelector('#btn-back').onclick = () => { location.hash = '#/objects'; };
      root.querySelector('#btn-reveal').onclick = onReveal;
      root.querySelectorAll('[data-action]').forEach((btn) => {
        btn.onclick = () => onTransition(btn.dataset.action, btn.textContent.trim(),
          btn.dataset.needReason === 'true');
      });
      const releaseLink = root.querySelector('#btn-release-link');
      if (releaseLink) {
        releaseLink.onclick = () => {
          if (detail.releaseAssessment) {
            globalThis.__openReleaseAssessment = detail.releaseAssessment.id;
          }
          location.hash = '#/release';
        };
      }
      root.querySelectorAll('[data-case]').forEach((el) => {
        el.onclick = () => {
          globalThis.__openViolationCase = Number(el.dataset.case);
          location.hash = '#/violations';
        };
      });
      loadTracks();
      loadAudits();
    }

    async function onReveal() {
      let reason;
      try {
        reason = await UI.confirmModal({
          title: '查看矫正对象全名',
          icon: '🔐',
          warn: '对象姓名属敏感信息，本次查看将记录查看人、理由与时间，接受事后审计。',
          bodyHtml: `即将查看 <span class="target-name">${UI.esc(o.maskedName)}（${UI.esc(o.correctionNo)}）</span> 的真实姓名。`,
          reasonLabel: '查看理由',
          reasonPlaceholder: '司法所核查、训诫谈话、应急处置等工作需要',
          requireReason: true,
          confirmText: '确认查看并留痕',
        });
      } catch { return; }

      try {
        const res = await Api.post('/objects/' + o.id + '/reveal-name', { reason });
        root.querySelector('#btn-reveal').style.display = 'none';
        const slot = root.querySelector('#fullname-slot');
        slot.style.display = 'inline';
        slot.textContent = res.fullName;
        UI.toast('全名已展示，查看行为已留痕', 'success');
        loadAudits();
      } catch (e) {
        UI.toast(e.message, 'error');
      }
    }

    async function loadAudits() {
      const slot = root.querySelector('#audit-slot');
      if (!slot) return;
      try {
        const audits = await Api.get('/objects/' + o.id + '/name-audits');
        if (!audits.length) { slot.innerHTML = ''; return; }
        slot.innerHTML = `
          <div style="margin-top:14px;border-top:1px solid var(--hairline);padding-top:10px">
            <div style="font-size:12.5px;font-weight:600;color:var(--ink-secondary);margin-bottom:6px">
              全名查看留痕（最近 ${audits.length} 次）
            </div>
            <div class="violation-list">
              ${audits.slice(0, 5).map((a) => `
                <div class="violation-item">
                  <span style="margin-top:2px">👁</span>
                  <div class="v-body">
                    <div class="v-detail">${UI.esc(a.viewerName)}：${UI.esc(a.reason)}</div>
                    <div class="v-meta">${UI.fmtTzFull(a.viewedAt, o.timezone)}</div>
                  </div>
                </div>`).join('')}
            </div>
          </div>`;
      } catch { /* 留痕记录加载失败不阻塞主档 */ }
    }

    async function onTransition(target, label, needReason) {
      let reason = null;
      try {
        reason = await UI.confirmModal({
          title: '确认状态变更：' + label,
          icon: '🔄',
          warn: '状态流转受状态机约束，非法回退会被系统拒绝并记录；该操作将写入流转留痕。',
          bodyHtml: `对象 <span class="target-name">${UI.esc(o.maskedName)}</span>：
            「${UI.STATUS_LABEL[o.status]}」→「${UI.STATUS_LABEL[target]}」`,
          requireReason: needReason,
          reasonLabel: '变更理由',
          reasonPlaceholder: '如：请假至县医院陪护，9 月 20 日前销假',
          confirmText: '确认执行',
          danger: ['ADMONISHED', 'REIMPRISONED'].includes(target),
        });
      } catch { return; }

      try {
        await Api.post('/objects/' + o.id + '/transition', { targetStatus: target, reason });
        UI.toast('状态已变更为「' + UI.STATUS_LABEL[target] + '」', 'success');
        const fresh = await Api.get('/objects/' + id);
        detail = fresh;
        Object.assign(o, fresh.object);
        render();
      } catch (e) {
        if (e.code === 'INVALID_TRANSITION') {
          UI.alertModal('状态机已拦截该变更',
            `<div style="font-size:13.5px;line-height:1.7">${UI.esc(e.message)}</div>`, '⛔');
        } else {
          UI.toast(e.message, 'error');
        }
      }
    }

    async function loadTracks() {
      const slot = root.querySelector('#tracks-slot');
      try {
        const tracks = await Api.get('/objects/' + o.id + '/tracks');
        if (!tracks.length) {
          slot.innerHTML = '<div style="color:var(--ink-muted);font-size:13px">暂无轨迹点</div>';
          return;
        }
        const shown = tracks.slice(-12).reverse();
        slot.innerHTML = `
          <div class="table-wrap">
            <table class="data">
              <thead><tr><th>采集时间</th><th>坐标</th><th>来源</th><th>围栏</th></tr></thead>
              <tbody>
                ${shown.map((t) => `
                  <tr>
                    <td data-label="采集时间" style="white-space:nowrap">${UI.fmtTzFull(t.pointTime, o.timezone)}</td>
                    <td data-label="坐标" style="font-variant-numeric:tabular-nums;font-size:12.5px">
                      ${t.lat.toFixed(5)}, ${t.lng.toFixed(5)}</td>
                    <td data-label="来源">${t.offlineCaptured
                      ? '<span class="badge" style="color:var(--warning);background:var(--warning-bg)">离线补传</span>'
                      : '<span class="badge green">实时</span>'}</td>
                    <td data-label="围栏">${t.outsideFence
                      ? '<span class="badge red">越界</span>'
                      : '<span class="badge green">内</span>'}</td>
                  </tr>`).join('')}
              </tbody>
            </table>
          </div>
          ${tracks.length > 12 ? `<div style="font-size:12px;color:var(--ink-muted);margin-top:6px">仅显示最近 12 个点</div>` : ''}`;
      } catch (e) {
        slot.innerHTML = `<div style="color:var(--critical);font-size:13px">轨迹加载失败：${UI.esc(e.message)}</div>`;
      }
    }
  };

  function forbiddenHtml(message) {
    return `
      <div class="state-box forbidden">
        <div class="ico">🚫</div>
        <h3>无权访问该档案</h3>
        <p>${UI.esc(message)}</p>
        <p style="color:var(--ink-muted)">矫正对象之间数据相互隔离，每位对象只能查看本人档案；
          司法所干警只能查看本所对象。本次越权尝试已在服务端被拒绝。</p>
        <button class="btn primary" id="btn-back">返回档案列表</button>
      </div>`;
  }

  function stateHtml(icon, title, message) {
    return `<div class="state-box"><div class="ico">${icon}</div>
      <h3>${UI.esc(title)}</h3><p>${UI.esc(message || '')}</p></div>`;
  }
})(window);
