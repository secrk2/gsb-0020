/* 违规处置：红点/未报到等预警登记为案件 → 训诫/收监/驳回/撤销，全程留痕 */
(function (global) {
  const Views = global.Views || (global.Views = {});

  const TABS = [
    ['', '全部'], ['REGISTERED', '待处置'], ['ADMONISHED', '已训诫'],
    ['REIMPRISONED', '已收监'], ['REJECTED', '已驳回'], ['REVOKED', '已撤销'],
  ];
  const REASON_TYPES = [
    ['GEOFENCE_BREACH', '越界'], ['FORBIDDEN_ZONE', '禁区闯入'],
    ['ABSENT', '未按日报到'], ['ADMONISH', '训诫'], ['OTHER', '其他违规'],
  ];
  const STATUS_BADGE = {
    REGISTERED: 'badge', ADMONISHED: 'badge green', REIMPRISONED: 'badge red',
    REJECTED: 'badge gray', REVOKED: 'badge gray',
  };

  Views.violations = async function (root) {
    const session = Api.getSession();
    const state = { status: '', officeId: '', offices: [], objects: [], selectedId: null, preEvent: null };

    root.innerHTML = `
      <div class="page-head">
        <h2>⚖️ 违规处置</h2>
        <div class="desc">越界、禁区闯入、未报到等预警先登记为处置案件；同一对象同一事由 24 小时内只立一条。
          登记后可训诫或提请收监（同步走矫正状态机），也可驳回、撤销；每一步操作人与理由均留痕，结论不得直接涂改。</div>
      </div>
      <div class="monitor-layout">
        <aside class="card monitor-list-card">
          <div class="card-title">处置案件 <span class="sub" id="vc-summary">—</span></div>
          <div style="display:flex;gap:6px;flex-wrap:wrap;margin-bottom:8px">
            <button class="btn sm primary" id="vc-new">手工登记</button>
          </div>
          <div class="tab-strip" id="vc-tabs">
            ${TABS.map(([k, l]) => `<button class="tab ${k === state.status ? 'active' : ''}" data-st="${k}">${l}</button>`).join('')}
          </div>
          ${session.role === 'SUPERVISOR' ? `
            <select class="input" id="vc-office" style="margin:8px 0;width:100%">
              <option value="">全部司法所</option>
            </select>` : ''}
          <div id="vc-list" class="monitor-list"><div class="skeleton">案件加载中…</div></div>
        </aside>
        <section class="monitor-main" id="vc-main">
          <div class="state-box"><div class="ico">⚖️</div><h3>请选择左侧案件</h3>
            <p>查看每条处置动作的操作人、理由与时间，或对待处置案件作出训诫、收监、驳回、撤销。</p></div>
        </section>
      </div>`;

    const listEl = root.querySelector('#vc-list');
    const mainEl = root.querySelector('#vc-main');
    const tabsEl = root.querySelector('#vc-tabs');
    const officeSel = root.querySelector('#vc-office');

    if (session.role === 'SUPERVISOR') {
      try {
        const dash = await Api.get('/dashboard');
        state.offices = dash.offices;
        officeSel.innerHTML = '<option value="">全部司法所</option>'
          + state.offices.map((o) => `<option value="${o.officeId}">${UI.esc(o.officeName)}</option>`).join('');
        officeSel.onchange = () => { state.officeId = officeSel.value; loadList(); };
      } catch { /* 筛选可选 */ }
    }
    try {
      const all = await Api.get('/objects');
      // 已解除/收监为矫正终态，不能再登记案件，下拉中不提供
      state.objects = all.filter((o) => o.status !== 'RELEASED' && o.status !== 'REIMPRISONED');
    } catch { state.objects = []; }

    tabsEl.querySelectorAll('.tab').forEach((b) => {
      b.onclick = () => {
        state.status = b.dataset.st;
        tabsEl.querySelectorAll('.tab').forEach((x) => x.classList.toggle('active', x === b));
        loadList();
      };
    });
    root.querySelector('#vc-new').onclick = () => registerModal(null);

    async function loadList() {
      listEl.innerHTML = '<div class="skeleton">加载中…</div>';
      const params = new URLSearchParams();
      if (state.status) params.set('status', state.status);
      if (state.officeId) params.set('officeId', state.officeId);
      try {
        const items = await Api.get('/violations/cases' + (params.toString() ? '?' + params : ''));
        renderList(items);
      } catch (e) {
        listEl.innerHTML = `<div class="state-box mini"><div class="ico">⚠️</div><h3>案件加载失败</h3>
          <p>${UI.esc(e.message)}</p><button class="btn sm primary" id="vc-retry">重新加载</button></div>`;
        listEl.querySelector('#vc-retry').onclick = loadList;
      }
    }

    function renderList(items) {
      const openN = items.filter((c) => c.status === 'REGISTERED').length;
      root.querySelector('#vc-summary').textContent = `${items.length} 件 · 待处置 ${openN}`;
      if (!items.length) {
        listEl.innerHTML = `<div class="state-box mini"><div class="ico">🗂️</div>
          <h3>暂无此类案件</h3><p>可由作战台红点“登记处置”，或手工登记。</p></div>`;
        return;
      }
      listEl.innerHTML = items.map((c) => `
        <button class="mon-item ${state.selectedId === c.id ? 'active' : ''}" data-id="${c.id}">
          <div style="display:flex;justify-content:space-between;gap:6px;width:100%;align-items:center">
            <b>${UI.esc(c.caseNo)}</b>
            <span class="${STATUS_BADGE[c.status] || 'badge'}">${UI.esc(c.statusLabel)}</span>
          </div>
          <div style="width:100%;margin-top:3px">
            <span class="badge red" style="margin-right:4px">${UI.esc(c.reasonTypeLabel)}</span>
            <span>${UI.esc(c.maskedName)} · ${UI.esc(c.correctionNo)}</span>
          </div>
          <div class="mon-meta" style="width:100%">${UI.esc(c.officeName)} · ${UI.fmtTz(c.registeredAt, c.timezone)}
            ${c.mergedEventCount > 1 ? ` · 合并预警 ${c.mergedEventCount} 条` : ''}</div>
        </button>`).join('');
      listEl.querySelectorAll('.mon-item').forEach((b) => {
        b.onclick = () => openDetail(Number(b.dataset.id));
      });
    }

    async function openDetail(id) {
      state.selectedId = id;
      mainEl.innerHTML = '<div class="skeleton">案件加载中…</div>';
      let d;
      try {
        d = await Api.get('/violations/cases/' + id);
      } catch (e) {
        mainEl.innerHTML = ErrorState('案件加载失败', e.message);
        return;
      }
      renderDetail(d);
    }

    function renderDetail(d) {
      const c = d.caseInfo;
      const frozen = d.allowedActions.length === 0;
      mainEl.innerHTML = `
        <div class="card">
          <div class="card-title">案件 ${UI.esc(c.caseNo)}
            <span class="${STATUS_BADGE[c.status] || 'badge'}" style="margin-left:8px">${UI.esc(c.statusLabel)}</span>
          </div>
          <dl class="kv">
            <dt>对象</dt><dd><b>${UI.esc(c.maskedName)}</b> · ${UI.esc(c.correctionNo)} · ${UI.esc(c.officeName)}</dd>
            <dt>违规事由</dt><dd><span class="badge red">${UI.esc(c.reasonTypeLabel)}</span></dd>
            <dt>登记事由</dt><dd>${UI.esc(c.summary)}</dd>
            <dt>登记人/时间</dt><dd>${UI.esc(c.registeredByName)} · ${UI.fmtTzFull(c.registeredAt, c.timezone)}（${UI.esc(c.timezone)}）</dd>
            ${c.closedAt ? `<dt>办结时间</dt><dd>${UI.fmtTzFull(c.closedAt, c.timezone)}</dd>` : ''}
            <dt>对象当前状态</dt><dd>${UI.statusBadge(d.objectStatus)}</dd>
          </dl>
          ${frozen ? `<div class="confirm-warn">该案已办结（${UI.esc(c.statusLabel)}）。
            处置结论已随每一步动作永久留痕，<b>不允许直接修改结论</b>；如认为结论有误，须按程序另行登记新案件。</div>` : `
          <div style="display:flex;gap:8px;flex-wrap:wrap;margin-top:12px">
            <button class="btn danger sm" data-act="ADMONISH">予以训诫</button>
            <button class="btn danger sm" data-act="REIMPRISON">提请收监</button>
            <button class="btn sm" data-act="REJECT">驳回</button>
            <button class="btn sm" data-act="REVOKE">撤销案件</button>
          </div>
          <div id="vc-act-result" style="margin-top:10px"></div>`}
        </div>

        <div class="card">
          <div class="card-title">🧾 处置留痕
            <span class="sub">每一步谁操作、因为什么</span>
          </div>
          <div class="timeline">
            ${d.actions.map((a) => `
              <div class="tl-item">
                <div class="tl-line"><b>${UI.esc(a.actionLabel)}</b>
                  <span class="${STATUS_BADGE[a.statusAfter] || 'badge'}" style="margin-left:6px">${UI.esc(a.statusAfterLabel)}</span></div>
                <div class="tl-meta">${UI.esc(a.operatorName)} · ${UI.fmtTzFull(a.createdAt, c.timezone)}
                  · ${UI.esc(a.reason)}</div>
                ${a.detail ? `<div class="tl-meta" style="color:var(--good)">↳ ${UI.esc(a.detail)}</div>` : ''}
              </div>`).join('')}
          </div>
        </div>

        <div class="card">
          <div class="card-title">🚨 关联原始预警 <span class="sub">${d.events.length} 条（同一时间窗合并）</span></div>
          <div class="violation-list">
            ${d.events.length ? d.events.map((v) => `
              <div class="violation-item">
                <span style="margin-top:4px">${v.type === 'GEOFENCE_BREACH' ? '📍' : v.type === 'ABSENT' ? '🚨' : '⚠️'}</span>
                <div class="v-body">
                  <div class="v-detail"><span class="badge red" style="margin-right:6px">${UI.esc(v.typeLabel)}</span>${UI.esc(v.detail)}</div>
                  <div class="v-meta">${UI.fmtTzFull(v.eventTime, c.timezone)}（${UI.esc(c.timezone)}）· 已核销</div>
                </div>
              </div>`).join('') : '<div style="color:var(--ink-muted);font-size:13px">人工登记案件，无原始红点</div>'}
          </div>
          <div style="margin-top:10px"><button class="btn sm" id="vc-goto-obj">查看对象档案</button></div>
        </div>`;

      mainEl.querySelector('#vc-goto-obj').onclick = () => { location.hash = '#/objects/' + c.objectId; };
      if (!frozen) {
        mainEl.querySelectorAll('[data-act]').forEach((btn) => {
          btn.onclick = () => doAction(btn.dataset.act, c);
        });
      }
    }

    const ACT_META = {
      ADMONISH: { path: 'admonish', title: '予以训诫', danger: true,
        placeholder: '如：逾时未报到且拒不说明情况，依规予以训诫，责令书面检查',
        warn: '训诫将同步把矫正档案状态机推进至「训诫」，非法当前状态会被系统拒绝并返回原因。' },
      REIMPRISON: { path: 'reimprison', title: '提请收监', danger: true,
        placeholder: '如：违反监管规定情节严重，提请撤销缓刑收监执行',
        warn: '收监为矫正流程终态，将同步把档案状态机推进至「收监」，不可回退。' },
      REJECT: { path: 'reject', title: '驳回处置', danger: false,
        placeholder: '如：经实地核查为设备故障误报，证据不足，不予处置',
        warn: '驳回后案件关闭，所附红点核销；驳回理由将永久留痕。' },
      REVOKE: { path: 'revoke', title: '撤销案件', danger: false,
        placeholder: '如：发现系重复立案/对象登记错误，撤销该案件',
        warn: '撤销后案件关闭，所附红点核销；撤销理由将永久留痕。' },
    };

    async function doAction(act, c) {
      const meta = ACT_META[act];
      let reason;
      try {
        reason = await UI.confirmModal({
          title: meta.title + '：' + c.caseNo,
          icon: '⚖️',
          warn: meta.warn,
          bodyHtml: `对象 <span class="target-name">${UI.esc(c.maskedName)}（${UI.esc(c.correctionNo)}）</span>，
            事由「${UI.esc(c.reasonTypeLabel)}」。该动作与理由将写入案件留痕。`,
          requireReason: true,
          reasonLabel: '处置理由/依据',
          reasonPlaceholder: meta.placeholder,
          confirmText: '确认' + meta.title,
          danger: meta.danger,
        });
      } catch { return; }
      try {
        await Api.post('/violations/cases/' + c.id + '/' + meta.path, { reason });
        UI.toast(meta.title + '已完成并留痕', 'success');
        await Promise.all([loadList(), openDetail(c.id)]);
      } catch (e) {
        if (e.code === 'INVALID_TRANSITION' || e.code === 'INVALID_ACTION') {
          UI.alertModal('操作被状态机拦截', `<div style="font-size:13.5px;line-height:1.7">${UI.esc(e.message)}</div>`, '⛔');
        } else {
          UI.toast(e.message, 'error');
        }
      }
    }

    // ---------- 登记（手工 / 红点入口） ----------
    async function registerModal(prefill) {
      const objectOptions = state.objects.map((o) =>
        `<option value="${o.id}" ${prefill && prefill.objectId === o.id ? 'selected' : ''}>
          ${UI.esc(o.maskedName)}（${UI.esc(o.correctionNo)}·${UI.esc(o.officeName)}）</option>`).join('');
      const reason = await formModal({
        title: '登记违规处置案件',
        icon: '📥',
        warn: '同一对象同一事由 24 小时内已有待处置案件时，本次登记将自动并入原案件，不另立案。',
        fields: [
          { name: 'offenderId', label: '矫正对象', type: 'select',
            options: objectOptions, value: prefill ? String(prefill.objectId) : '' },
          { name: 'reasonType', label: '违规事由', type: 'select',
            options: REASON_TYPES.map(([v, l]) => ({ value: v, label: l })),
            value: prefill ? prefill.reasonType : 'GEOFENCE_BREACH' },
          { name: 'reason', label: '登记事由（必填，≥4 字）', type: 'textarea',
            placeholder: '如：夜查发现腕表定位连续越界，需约谈核实',
            value: prefill ? prefill.detail : '' },
        ],
        confirmText: '登记受理',
      }).catch(() => null);
      if (!reason) return;
      try {
        const body = { offenderId: Number(reason.offenderId), reasonType: reason.reasonType, reason: reason.reason };
        if (prefill && prefill.eventId) body.eventId = prefill.eventId;
        const res = await Api.post('/violations/cases', body);
        UI.toast('已登记案件 ' + res.caseNo + (res.mergedEventCount > 1 ? `，并入 ${res.mergedEventCount} 条预警` : ''), 'success');
        state.status = '';
        tabsEl.querySelectorAll('.tab').forEach((x) => x.classList.toggle('active', x.dataset.st === ''));
        await loadList();
        await openDetail(res.id);
      } catch (e) {
        UI.toast(e.message, 'error');
      }
    }

    // 简易表单弹窗：fields = [{name,label,type,options:[{value,label}],value,placeholder}]
    function formModal(opt) {
      return new Promise((resolve, reject) => {
        const mask = document.createElement('div');
        mask.className = 'modal-mask';
        mask.innerHTML = `
          <div class="modal warn" role="dialog">
            <div class="modal-head">${opt.icon || ''} ${UI.esc(opt.title || '请填写')}</div>
            <div class="modal-body">
              ${opt.warn ? `<div class="confirm-warn">⚠️ ${UI.esc(opt.warn)}</div>` : ''}
              ${opt.fields.map((f) => fieldHtml(f)).join('')}
              <div class="modal-error" id="fm-err" style="display:none"></div>
            </div>
            <div class="modal-foot">
              <button class="btn" id="fm-cancel">取消</button>
              <button class="btn primary" id="fm-ok">${UI.esc(opt.confirmText || '确认')}</button>
            </div>
          </div>`;
        document.getElementById('modal-root').appendChild(mask);
        const close = () => mask.remove();
        mask.querySelector('#fm-cancel').onclick = () => { close(); reject(new Error('cancel')); };
        mask.addEventListener('click', (e) => { if (e.target === mask) { close(); reject(new Error('cancel')); } });
        mask.querySelector('#fm-ok').onclick = () => {
          const out = {};
          for (const f of opt.fields) {
            const el = mask.querySelector('[name="' + f.name + '"]');
            out[f.name] = el ? el.value.trim() : '';
          }
          if (!out.offenderId) { return showErr('请选择矫正对象'); }
          if (out.reason && out.reason.length < 4) { return showErr('登记事由不少于 4 个字，将随案件留痕'); }
          close();
          resolve(out);
        };
        function showErr(m) { const e = mask.querySelector('#fm-err'); e.style.display = 'block'; e.className = 'login-error'; e.textContent = m; }
        function fieldHtml(f) {
          if (f.type === 'select') {
            return `<div class="field"><label>${UI.esc(f.label)}</label>
              <select class="input" name="${f.name}">
                ${f.options.map((o) => typeof o === 'string' ? o
                  : `<option value="${o.value}" ${String(f.value) === String(o.value) ? 'selected' : ''}>${UI.esc(o.label)}</option>`).join('')}
              </select></div>`;
          }
          if (f.type === 'textarea') {
            return `<div class="field"><label>${UI.esc(f.label)}</label>
              <textarea class="input" name="${f.name}" maxlength="256" placeholder="${UI.esc(f.placeholder || '')}">${UI.esc(f.value || '')}</textarea></div>`;
          }
          return `<div class="field"><label>${UI.esc(f.label)}</label>
            <input class="input" name="${f.name}" value="${UI.esc(f.value || '')}" placeholder="${UI.esc(f.placeholder || '')}"/></div>`;
        }
      });
    }

    // 供作战台红点登记入口调用
    global.__openViolationRegister = registerModal;

    await loadList();

    // 从作战台红点跳转到本页时携带的待登记参数
    const pending = global.__pendingRegister;
    if (pending) {
      global.__pendingRegister = null;
      registerModal(pending);
    }
    // 从对象档案跳转来打开指定案件
    const openId = global.__openViolationCase;
    if (openId) {
      global.__openViolationCase = null;
      openDetail(openId);
    }
  };
})(window);
