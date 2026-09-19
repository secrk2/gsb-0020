/* 违规处置中心：红点登记受理 → 训诫/收监/驳回/撤销，全程留痕；同一对象同一事由时间窗只一条 */
(function (global) {
  const Views = global.Views || (global.Views = {});

  const STATUS_LABEL = {
    REGISTERED: '已登记·待处置', ADMONISHED: '训诫处置', REIMPRISONED: '收监处置',
    REJECTED: '驳回', REVOKED: '已撤销',
  };
  const STATUS_BADGE = {
    REGISTERED: 'red', ADMONISHED: '', REIMPRISONED: 'red', REJECTED: 'gray', REVOKED: 'gray',
  };
  const EVENT_TO_CATEGORY = {
    GEOFENCE_BREACH: 'GEOFENCE_BREACH', FORBIDDEN_ZONE: 'FORBIDDEN_ZONE', ABSENT: 'ABSENT',
  };
  const FILTERS = [
    ['', '全部'], ['REGISTERED', '待处置'], ['ADMONISHED', '训诫'],
    ['REIMPRISONED', '收监'], ['REJECTED', '驳回'], ['REVOKED', '撤销'],
  ];

  Views.disposals = async function (root, id) {
    if (id) return renderDetail(root, Number(id));
    return renderList(root);
  };

  // ---------------- 列表 ----------------
  async function renderList(root) {
    root.innerHTML = `<div class="skeleton">处置数据加载中…</div>`;
    let openEvents = [];
    let cases = [];
    let filter = 'REGISTERED';

    async function load() {
      const params = filter ? '?status=' + filter : '';
      [openEvents, cases] = await Promise.all([
        Api.get('/disposals/open-events'),
        Api.get('/disposals' + params),
      ]);
      draw();
    }

    function draw() {
      root.innerHTML = `
        <div class="page-head" style="display:flex;align-items:center;gap:12px;flex-wrap:wrap">
          <h2 style="margin:0">⚖️ 违规处置</h2>
          <span style="flex:1"></span>
          <button class="btn sm" id="btn-manual">✍ 手工登记违规</button>
        </div>
        <div class="desc" style="margin:6px 0 14px">
          越界、未报到等红点先<b>登记受理</b>成处置单，再予以训诫或提请收监，也可驳回、撤销；
          每一步操作人与理由都留痕，结论只能沿流程推进、不能直接改。同一对象同一事由 12 小时内只合成一条。
        </div>

        <div class="card">
          <div class="card-title">🧯 待登记受理的红点
            <span class="sub">${openEvents.length} 条未受理</span>
          </div>
          <div class="violation-list">
            ${openEvents.length ? openEvents.map(openItem).join('')
              : '<div style="color:var(--good);font-size:13px;padding:6px 0">暂无待登记红点（已登记的在处置单里继续办理）</div>'}
          </div>
        </div>

        <div class="card">
          <div class="filter-bar">
            <label style="font-size:13px;color:var(--ink-secondary)">处置单</label>
            <div class="seg" id="case-filter">
              ${FILTERS.map(([v, l]) => `<button class="seg-btn ${v === filter ? 'on' : ''}" data-v="${v}">${l}</button>`).join('')}
            </div>
          </div>
          <div class="table-wrap">
            ${cases.length ? tableHtml(cases) : `<div class="state-box"><div class="ico">🗂️</div>
              <h3>该过滤下暂无处置单</h3></div>`}
          </div>
        </div>`;

      root.querySelector('#btn-manual').onclick = onManual;
      root.querySelectorAll('#case-filter .seg-btn').forEach((b) => {
        b.onclick = async () => { filter = b.dataset.v; await load(); };
      });
      root.querySelectorAll('[data-event-id]').forEach((el) => {
        el.onclick = () => onRegisterEvent(el.dataset);
      });
      root.querySelectorAll('tr[data-case-id]').forEach((tr) => {
        tr.onclick = () => { location.hash = '#/disposals/' + tr.dataset.caseId; };
      });
    }

    function openItem(v) {
      return `
        <div class="violation-item">
          <span class="red-dot" style="margin-top:6px"></span>
          <div class="v-body">
            <div class="v-detail">
              <span class="badge red" style="margin-right:6px">${UI.esc(v.typeLabel)}</span>${UI.esc(v.detail)}
            </div>
            <div class="v-meta">${UI.esc(v.maskedName)} · ${UI.esc(v.correctionNo)} · ${UI.esc(v.officeName)}
              · ${UI.fmtTzFull(v.eventTime, v.timezone)}（${UI.esc(v.timezone)}）</div>
          </div>
          <button class="btn sm primary" data-event-id="${v.id}" data-object-id="${v.objectId}"
            data-type="${v.type}" data-label="${UI.esc(v.typeLabel)}" data-name="${UI.esc(v.maskedName)}">
            登记受理</button>
        </div>`;
    }

    function tableHtml(list) {
      return `
        <table class="data">
          <thead><tr><th>处置单号</th><th>对象</th><th>事由</th><th>状态</th>
            <th>合并事件</th><th>登记人</th><th>登记时间</th><th></th></tr></thead>
          <tbody>
            ${list.map((c) => `
              <tr class="clickable" data-case-id="${c.id}">
                <td data-label="单号" style="white-space:nowrap">${UI.esc(c.disposalNo)}</td>
                <td data-label="对象"><b>${UI.esc(c.maskedName)}</b>
                  <div style="font-size:12px;color:var(--ink-muted)">${UI.esc(c.correctionNo)}</div></td>
                <td data-label="事由">${UI.esc(c.categoryLabel)}<span class="sub"> · ${c.sourceLabel}</span></td>
                <td data-label="状态"><span class="badge ${STATUS_BADGE[c.status] || ''}">${UI.esc(c.statusLabel)}</span></td>
                <td data-label="合并事件">${c.eventCount} 条</td>
                <td data-label="登记人">${UI.esc(c.registeredByName)}</td>
                <td data-label="登记时间" style="white-space:nowrap">${UI.fmtTzFull(c.registeredAt, c.timezone)}</td>
                <td data-label=""><button class="btn sm" data-go="${c.id}">办理</button></td>
              </tr>`).join('')}
          </tbody>
        </table>`;
    }

    async function onRegisterEvent(ds) {
      const category = EVENT_TO_CATEGORY[ds.type] || 'OTHER';
      let reason;
      try {
        reason = await UI.confirmModal({
          title: '登记受理：' + ds.label,
          icon: '⚖️',
          warn: '登记后该红点转入处置单待处置，从作战台未处置红点移除；同一对象同一事由 12 小时内的事件会并入这一单，不再重复开单。',
          bodyHtml: `将把 <span class="target-name">${UI.esc(ds.name)}</span> 的「${UI.esc(ds.label)}」事件登记为违规处置单。`,
          reasonLabel: '登记理由',
          reasonPlaceholder: '如：监控发现越界，电话联系不上，登记并派员核查…',
          requireReason: true,
          confirmText: '确认登记受理',
        });
      } catch { return; }
      try {
        const detail = await Api.post('/disposals/from-events', {
          objectId: Number(ds.objectId), category, reason,
        });
        UI.toast('已登记受理，处置单 ' + detail.disposal.disposalNo, 'success');
        location.hash = '#/disposals/' + detail.disposal.id;
      } catch (e) { UI.toast(e.message, 'error'); }
    }

    async function onManual() {
      try {
        const form = await manualModal();
        const found = await Api.get('/objects?keyword=' + encodeURIComponent(form.no.trim()));
        const obj = (found || []).find((o) => o.correctionNo.toUpperCase() === form.no.trim().toUpperCase())
          || (found || [])[0];
        if (!obj) { UI.toast('未找到该矫正编号对应的对象', 'error'); return; }
        const detail = await Api.post('/disposals/manual', {
          objectId: obj.id, category: form.category, title: form.title, detail: form.detail, reason: form.reason,
        });
        UI.toast('已手工登记处置单 ' + detail.disposal.disposalNo, 'success');
        location.hash = '#/disposals/' + detail.disposal.id;
      } catch (e) {
        if (e.message === 'cancel') return;
        UI.toast(e.message || '操作失败', 'error');
      }
    }

    await load();
  }

  // ---------------- 详情 ----------------
  async function renderDetail(root, caseId) {
    root.innerHTML = `<div class="skeleton">处置单加载中…</div>`;
    let d;
    async function reload() { d = await Api.get('/disposals/' + caseId); draw(); }

    function draw() {
      const c = d.disposal;
      const tz = c.timezone;
      const pending = c.status === 'REGISTERED';
      root.innerHTML = `
        <div class="page-head" style="display:flex;align-items:center;gap:12px;flex-wrap:wrap">
          <button class="btn sm" id="btn-back">← 返回处置列表</button>
          <h2 style="margin:0">${UI.esc(c.title)}</h2>
          <span class="badge ${STATUS_BADGE[c.status] || ''}">${UI.esc(c.statusLabel)}</span>
          <span style="flex:1"></span>
          <button class="btn sm" id="btn-object">档案 ${UI.esc(c.correctionNo)}</button>
        </div>

        <div class="detail-grid">
          <div>
            <div class="card">
              <div class="card-title">📄 处置单信息
                <span class="sub">${UI.esc(c.disposalNo)} · ${c.sourceLabel}</span>
              </div>
              <dl class="kv">
                <dt>对象</dt><dd><b>${UI.esc(c.maskedName)}</b>（${UI.esc(c.correctionNo)}）· ${UI.esc(c.officeName)}</dd>
                <dt>违规事由</dt><dd><span class="badge red">${UI.esc(c.categoryLabel)}</span></dd>
                <dt>档案当前状态</dt><dd>${UI.esc(d.objectStatusLabel)}</dd>
                <dt>合并事件</dt><dd>${c.eventCount} 条（${c.firstEventAt ? UI.fmtTzFull(c.firstEventAt, tz) : '—'} ～ ${c.lastEventAt ? UI.fmtTzFull(c.lastEventAt, tz) : '—'}）</dd>
                <dt>情况说明</dt><dd>${UI.esc(c.detail || '—')}</dd>
                <dt>登记人</dt><dd>${UI.esc(c.registeredByName)} · ${UI.fmtTzFull(c.registeredAt, tz)}（${c.registerReason ? UI.esc(c.registerReason) : ''}）</dd>
                ${c.resolvedAt ? `<dt>办结时间</dt><dd>${UI.fmtTzFull(c.resolvedAt, tz)}</dd>` : ''}
              </dl>
            </div>

            <div class="card">
              <div class="card-title">🚨 合并的红点事件（${d.linkedEvents.length}）</div>
              <div class="violation-list">
                ${d.linkedEvents.length ? d.linkedEvents.map((e) => `
                  <div class="violation-item">
                    <span class="red-dot" style="margin-top:6px"></span>
                    <div class="v-body">
                      <div class="v-detail"><span class="badge red" style="margin-right:6px">${UI.esc(e.typeLabel)}</span>${UI.esc(e.detail)}</div>
                      <div class="v-meta">${UI.fmtTzFull(e.eventTime, tz)}（${UI.esc(tz)}）</div>
                    </div>
                  </div>`).join('') : '<div style="color:var(--ink-muted);font-size:13px">手工登记单，无关联红点事件</div>'}
              </div>
            </div>
          </div>

          <div>
            <div class="card">
              <div class="card-title">⚖️ 处置措施</div>
              ${pending ? `
                <div style="font-size:13px;color:var(--ink-secondary);margin-bottom:8px">
                  对象档案当前为「${UI.esc(d.objectStatusLabel)}」。训诫/收监会联动档案状态机，非法去向将被拒绝并说明原因。
                </div>
                <div class="transition-pad" style="display:flex;flex-direction:column;gap:8px">
                  <button class="btn primary" data-act="admonish">⚠️ 予以训诫（档案→训诫）</button>
                  <button class="btn danger" data-act="reimprison">🔒 提请收监（档案→收监·终态）</button>
                  <button class="btn" data-act="reject">↩️ 驳回（经查不构成违规）</button>
                  <button class="btn" data-act="revoke">🗑️ 撤销（登记有误，红点退回）</button>
                </div>` : `
                <div class="confirm-warn">该处置单已办结为「${UI.esc(c.statusLabel)}」。
                  结论不可直接改写；训诫对象如需恢复在矫，请在档案页走「教育改正·恢复在矫」。</div>`}
            </div>

            <div class="card">
              <div class="card-title">🧾 处置留痕（只追加、不可改）</div>
              <div class="timeline">
                ${d.logs.map((l) => `
                  <div class="tl-item">
                    <div class="tl-line"><span class="badge ${l.action === 'ADMONISH' || l.action === 'REIMPRISON' ? 'red' : 'gray'}">${UI.esc(l.actionLabel)}</span>
                      → <b>${UI.esc(l.toStatusLabel)}</b></div>
                    <div class="tl-meta">${UI.esc(l.operatorName)} · ${UI.fmtTzFull(l.operatedAt, tz)}
                      ${l.reason ? '· ' + UI.esc(l.reason) : ''}</div>
                    ${l.detail ? `<div class="tl-meta" style="color:var(--ink-muted)">${UI.esc(l.detail)}</div>` : ''}
                  </div>`).join('')}
              </div>
            </div>
          </div>
        </div>`;

      root.querySelector('#btn-back').onclick = () => { location.hash = '#/disposals'; };
      root.querySelector('#btn-object').onclick = () => { location.hash = '#/objects/' + c.objectId; };
      root.querySelectorAll('[data-act]').forEach((b) => {
        b.onclick = () => onAction(b.dataset.act);
      });
    }

    async function onAction(act) {
      const map = {
        admonish: { path: 'admonish', label: '予以训诫', danger: false, target: 'ADMONISHED',
          warn: '将联动档案状态机把对象变更为「训诫」，并写状态流转与处置留痕。' },
        reimprison: { path: 'reimprison', label: '提请收监', danger: true, target: 'REIMPRISONED',
          warn: '收监为不可逆终态：将联动档案变更为「收监」，社区矫正流程终结。' },
        reject: { path: 'reject', label: '驳回处置', danger: false, target: 'REJECTED',
          warn: '经查不构成违规时驳回：处置单办结，相关红点确认为无效并核销。' },
        revoke: { path: 'revoke', label: '撤销处置单', danger: false, target: 'REVOKED',
          warn: '登记有误时撤销：处置单办结，原红点退回作战台待重新处置。' },
      }[act];
      let reason;
      try {
        reason = await UI.confirmModal({
          title: map.label, icon: '⚖️', warn: map.warn,
          bodyHtml: `处置单 <span class="target-name">${UI.esc(d.disposal.disposalNo)}</span>`,
          reasonLabel: act === 'reject' ? '驳回理由' : act === 'revoke' ? '撤销理由' : '处置理由',
          reasonPlaceholder: '请说明事实依据与处置理由（不少于 4 字，将留痕）',
          requireReason: true, confirmText: '确认并留痕', danger: map.danger,
        });
      } catch { return; }
      try {
        await Api.post('/disposals/' + caseId + '/' + map.path, { reason });
        UI.toast('已' + map.label + '并留痕', 'success');
        await reload();
      } catch (e) {
        if (e.code === 'INVALID_TRANSITION' || e.code === 'DISPOSAL_TRANSITION') {
          UI.alertModal('状态机已拦截',
            `<div style="font-size:13.5px;line-height:1.7">${UI.esc(e.message)}</div>`, '⛔');
        } else UI.toast(e.message, 'error');
      }
    }

    await reload();
  }

  // ---------------- 手工登记多字段弹窗 ----------------
  const CATS = [['GEOFENCE_BREACH', '越界'], ['FORBIDDEN_ZONE', '禁区闯入'],
    ['ABSENT', '未按日报到'], ['OTHER', '其他违规']];
  function manualModal() {
    return new Promise((resolve, reject) => {
      const rootEl = document.getElementById('modal-root');
      const mask = document.createElement('div');
      mask.className = 'modal-mask';
      mask.innerHTML = `
        <div class="modal warn" role="dialog">
          <div class="modal-head">✍ 手工登记违规处置</div>
          <div class="modal-body">
            <div class="confirm-warn">⚠ 适用于群众举报、当面核查等<b>没有自动红点</b>的情形；登记即留痕。</div>
            <div class="field" style="margin-top:10px"><label>矫正编号</label>
              <input class="input" id="m-no" placeholder="如 JWT26005" maxlength="32"/></div>
            <div class="field" style="margin-top:10px"><label>违规事由</label>
              <select class="input" id="m-cat">${CATS.map(([v, l]) => `<option value="${v}">${l}</option>`).join('')}</select></div>
            <div class="field" style="margin-top:10px"><label>标题（可留空）</label>
              <input class="input" id="m-title" maxlength="128" placeholder="一句话概括"/></div>
            <div class="field" style="margin-top:10px"><label>情况说明（可留空）</label>
              <textarea class="input" id="m-detail" maxlength="512" placeholder="时间、地点、核查情况…"></textarea></div>
            <div class="field" style="margin-top:10px"><label>登记理由（必填，留痕）</label>
              <textarea class="input" id="m-reason" maxlength="256" placeholder="不少于 4 字"></textarea></div>
            <div class="modal-error" id="m-err" style="display:none"></div>
          </div>
          <div class="modal-foot">
            <button class="btn" id="m-cancel">取消</button>
            <button class="btn primary" id="m-ok">确认登记</button>
          </div>
        </div>`;
      rootEl.appendChild(mask);
      const close = () => mask.remove();
      mask.querySelector('#m-cancel').onclick = () => { close(); reject(new Error('cancel')); };
      mask.addEventListener('click', (e) => { if (e.target === mask) { close(); reject(new Error('cancel')); } });
      mask.querySelector('#m-ok').onclick = () => {
        const no = mask.querySelector('#m-no').value.trim();
        const reason = mask.querySelector('#m-reason').value.trim();
        const err = mask.querySelector('#m-err');
        if (!no) { showErr(err, '请填写矫正编号'); return; }
        if (reason.length < 4) { showErr(err, '登记理由不少于 4 字'); return; }
        close();
        resolve({ no, category: mask.querySelector('#m-cat').value,
          title: mask.querySelector('#m-title').value.trim(),
          detail: mask.querySelector('#m-detail').value.trim(), reason });
      };
      function showErr(el, msg) { el.style.display = 'block'; el.className = 'login-error'; el.textContent = msg; }
    });
  }
})(window);
