/* 解除与评估：到期名单 → 生成评估报告 → 提交/审批/执行解除 → 永久标记、位置冻结 */
(function (global) {
  const Views = global.Views || (global.Views = {});

  const STATUS_BADGE = {
    DRAFT: 'badge', SUBMITTED: 'badge', APPROVED: 'badge green',
    REJECTED: 'badge red', DONE: 'badge green',
  };

  Views.release = async function (root) {
    const session = Api.getSession();
    const state = { tab: 'due', offices: [], selectedId: null };

    root.innerHTML = `
      <div class="page-head">
        <h2>📭 解除与评估</h2>
        <div class="desc">矫正期满（按对象所在司法所时区判定）先<b>生成评估报告</b>，客观数据生成时固化；
          报告经提交、审批通过后才能执行解除。解除后出具永久解除证明书，移出在矫名单与作战台红点、定位停止更新，
          档案仍可按编号检索。</div>
      </div>
      <div class="monitor-layout">
        <aside class="card monitor-list-card">
          <div class="card-title">名单 / 报告 <span class="sub" id="rl-summary">—</span></div>
          <div class="tab-strip">
            <button class="tab active" data-tab="due">到期/临期</button>
            <button class="tab" data-tab="all">全部报告</button>
            <button class="tab" data-tab="archive">归档检索</button>
          </div>
          <div id="rl-office-wrap"></div>
          <div id="rl-list" class="monitor-list"><div class="skeleton">加载中…</div></div>
        </aside>
        <section class="monitor-main" id="rl-main">
          <div class="state-box"><div class="ico">📭</div><h3>请选择左侧条目</h3>
            <p>到期对象先生成评估报告，再按状态机走完审批与执行解除。</p></div>
        </section>
      </div>`;

    const listEl = root.querySelector('#rl-list');
    const mainEl = root.querySelector('#rl-main');

    if (session.role === 'SUPERVISOR') {
      root.querySelector('#rl-office-wrap').innerHTML = `
        <select class="input" id="rl-office" style="margin:8px 0;width:100%">
          <option value="">全部司法所</option>
        </select>`;
    }

    root.querySelectorAll('.tab-strip .tab').forEach((b) => {
      b.onclick = () => {
        state.tab = b.dataset.tab;
        root.querySelectorAll('.tab-strip .tab').forEach((x) => x.classList.toggle('active', x === b));
        loadLeft();
      };
    });

    async function loadLeft() {
      listEl.innerHTML = '<div class="skeleton">加载中…</div>';
      try {
        if (state.tab === 'due') {
          const items = await Api.get('/release/due');
          renderDue(items);
        } else if (state.tab === 'all') {
          const items = await Api.get('/release/assessments');
          renderReports(items);
        } else {
          renderArchiveSearch();
        }
      } catch (e) {
        listEl.innerHTML = `<div class="state-box mini"><div class="ico">⚠️</div><h3>加载失败</h3>
          <p>${UI.esc(e.message)}</p><button class="btn sm primary" id="rl-retry">重新加载</button></div>`;
        const retry = listEl.querySelector('#rl-retry');
        if (retry) retry.onclick = loadLeft;
      }
    }

    function renderDue(items) {
      root.querySelector('#rl-summary').textContent = `${items.length} 人到期/临期（30 天内）`;
      if (!items.length) {
        listEl.innerHTML = `<div class="state-box mini"><div class="ico">✅</div>
          <h3>近期无到期对象</h3><p>矫正期满前 30 天的对象会出现在这里。</p></div>`;
        return;
      }
      listEl.innerHTML = items.map((d) => {
        const dueLabel = d.overdue
          ? `<span class="badge red">已过期 ${-d.daysToDue} 天</span>`
          : d.daysToDue <= 7
            ? `<span class="badge red">${d.daysToDue} 天后到期</span>`
            : `<span class="badge gray">${d.daysToDue} 天后</span>`;
        return `
        <button class="mon-item" data-id="${d.objectId}">
          <div style="display:flex;justify-content:space-between;gap:6px;width:100%;align-items:center">
            <b>${UI.esc(d.maskedName)}</b>${dueLabel}
          </div>
          <div style="width:100%;margin-top:3px">
            ${UI.esc(d.correctionNo)} · ${UI.esc(d.officeName)} · ${UI.statusBadge(d.status)}
          </div>
          <div class="mon-meta" style="width:100%">
            期满日 ${UI.esc(d.endDate)} · ${d.assessmentStatus
              ? `<span class="badge ${d.assessmentStatus === 'DONE' ? 'green' : ''}">${UI.esc(d.assessmentStatusLabel)}</span>`
              : '<span class="badge gray">未评估</span>'}
          </div>
        </button>`;
      }).join('');
      listEl.querySelectorAll('.mon-item').forEach((b) => {
        b.onclick = () => {
          const id = Number(b.dataset.id);
          const item = items.find((x) => x.objectId === id);
          if (item.assessmentId) { openAssessment(item.assessmentId); } else { objectPreview(id, item); }
        };
      });
    }

    function renderReports(items) {
      root.querySelector('#rl-summary').textContent = `${items.length} 份报告`;
      if (!items.length) {
        listEl.innerHTML = `<div class="state-box mini"><div class="ico">📄</div><h3>暂无评估报告</h3></div>`;
        return;
      }
      listEl.innerHTML = items.map((a) => `
        <button class="mon-item" data-id="${a.id}" data-kind="report">
          <div style="display:flex;justify-content:space-between;width:100%">
            <b>${UI.esc(a.reportNo)}</b>
            <span class="${STATUS_BADGE[a.status] || 'badge'}">${UI.esc(a.statusLabel)}</span>
          </div>
          <div style="width:100%;margin-top:3px">${UI.esc(a.maskedName)} · ${UI.esc(a.correctionNo)}</div>
          <div class="mon-meta" style="width:100%">${UI.esc(a.officeName)} · 期满 ${UI.esc(a.dueDate)}</div>
        </button>`).join('');
      listEl.querySelectorAll('.mon-item').forEach((b) => {
        b.onclick = () => openAssessment(Number(b.dataset.id));
      });
    }

    function renderArchiveSearch() {
      root.querySelector('#rl-summary').textContent = '按矫正编号查归档';
      listEl.innerHTML = `
        <div style="padding:10px">
          <div class="field">
            <label>矫正编号（如 JWT26004）</label>
            <input class="input" id="rl-search-input" placeholder="输入至少 3 位编号" />
          </div>
          <button class="btn primary sm" id="rl-search-btn" style="width:100%">检索档案</button>
          <div id="rl-search-result" style="margin-top:10px"></div>
        </div>`;
      listEl.querySelector('#rl-search-btn').onclick = doSearch;
      listEl.querySelector('#rl-search-input').onkeydown = (e) => { if (e.key === 'Enter') doSearch(); };
    }

    async function doSearch() {
      const no = listEl.querySelector('#rl-search-input').value.trim();
      const slot = listEl.querySelector('#rl-search-result');
      slot.innerHTML = '<div class="skeleton">检索中…</div>';
      try {
        const res = await Api.get('/release/archive/lookup?correctionNo=' + encodeURIComponent(no));
        if (!res.items.length) {
          slot.innerHTML = '<div style="color:var(--ink-muted);font-size:13px">数据范围内未找到该编号档案</div>';
          return;
        }
        slot.innerHTML = res.items.map((o) => `
          <button class="mon-item" data-oid="${o.objectId}" ${o.assessmentId ? 'data-aid="' + o.assessmentId + '"' : ''}>
            <div style="display:flex;justify-content:space-between;width:100%">
              <b>${UI.esc(o.correctionNo)}</b>${UI.statusBadge(o.status)}</div>
            <div style="width:100%;margin-top:3px">${UI.esc(o.maskedName)} · ${UI.esc(o.officeName)}</div>
            ${o.released ? `<div class="mon-meta" style="width:100%">📜 ${UI.esc(o.releaseCertificateNo)} · ${UI.fmtTz(o.releasedMarkedAt, o.timezone || 'Asia/Shanghai')} · 位置已冻结</div>` : ''}
          </button>`).join('');
        slot.querySelectorAll('.mon-item').forEach((b) => {
          b.onclick = () => {
            if (b.dataset.aid && Number(b.dataset.aid) > 0) { openAssessment(Number(b.dataset.aid)); }
            else { location.hash = '#/objects/' + b.dataset.oid; }
          };
        });
      } catch (e) {
        slot.innerHTML = `<div style="color:var(--critical);font-size:13px">${UI.esc(e.message)}</div>`;
      }
    }

    async function objectPreview(objectId, dueItem) {
      mainEl.innerHTML = '<div class="skeleton">加载中…</div>';
      let o;
      try {
        o = (await Api.get('/objects/' + objectId)).object;
      } catch (e) {
        mainEl.innerHTML = ErrorState('档案加载失败', e.message);
        return;
      }
      mainEl.innerHTML = `
        <div class="card">
          <div class="card-title">${UI.esc(o.maskedName)} · ${UI.esc(o.correctionNo)}</div>
          <dl class="kv">
            <dt>司法所</dt><dd>${UI.esc(o.officeName)}（${UI.esc(o.timezone)}）</dt>
            <dt>当前状态</dt><dd>${UI.statusBadge(o.status)}</dd>
            <dt>矫正期限</dt><dd>${UI.esc(o.startDate)} 至 <b>${UI.esc(o.endDate)}</b></dd>
            ${dueItem ? `<dt>距期满</dt><dd>${dueItem.overdue ? '已过期 ' + (-dueItem.daysToDue) + ' 天' : dueItem.daysToDue + ' 天'}</dd>` : ''}
          </dl>
          <div class="confirm-warn" id="gen-warn">评估须由<b>本所干警/区监管员</b>生成。报告生成时固化近 30 天报到双口径、
            定位活跃、越界次数、训诫次数与待处置案件情况，之后不再随业务数据变化。</div>
          <button class="btn primary sm" id="btn-gen">生成解除评估报告</button>
        </div>`;
      mainEl.querySelector('#btn-gen').onclick = generateReport(objectId);
    }

    function generateReport(objectId) {
      return async () => {
        const btn = mainEl.querySelector('#btn-gen');
        btn.disabled = true; btn.textContent = '生成中…';
        try {
          const a = await Api.post('/release/objects/' + objectId + '/assessment', {});
          UI.toast('评估报告 ' + a.reportNo + ' 已生成', 'success');
          await openAssessment(a.id);
        } catch (e) {
          UI.toast(e.message, 'error');
          btn.disabled = false; btn.textContent = '生成解除评估报告';
        }
      };
    }

    async function openAssessment(id) {
      state.selectedId = id;
      mainEl.innerHTML = '<div class="skeleton">报告加载中…</div>';
      let d;
      try {
        d = await Api.get('/release/assessments/' + id);
      } catch (e) {
        mainEl.innerHTML = ErrorState('报告加载失败', e.message);
        return;
      }
      renderAssessment(d);
    }

    function rate(v) { return v == null ? '—' : Math.round(v * 100) + '%'; }

    function renderAssessment(d) {
      const a = d.assessment;
      const can = new Set(a.allowedActions);
      mainEl.innerHTML = `
        <div class="card">
          <div class="card-title">评估报告 ${UI.esc(a.reportNo)}
            <span class="${STATUS_BADGE[a.status] || 'badge'}" style="margin-left:8px">${UI.esc(a.statusLabel)}</span>
            ${a.releaseCertificateNo ? `<span class="badge green" style="margin-left:6px">📜 永久标记 ${UI.esc(a.releaseCertificateNo)}</span>` : ''}
          </div>
          <dl class="kv">
            <dt>对象</dt><dd><b>${UI.esc(a.maskedName)}</b> · ${UI.esc(a.correctionNo)} · ${UI.esc(a.officeName)}</dd>
            <dt>罪名/期限</dt><dd>${UI.esc(d.charge || '—')} · ${UI.esc(d.startDate)} 至 ${UI.esc(d.endDate)}</dd>
            <dt>期满日</dt><dd>${UI.esc(a.dueDate)}</dd>
            <dt>打卡天数口径</dt><dd><b>${rate(a.checkinDayRate)}</b>（近 30 天有报到天数 ÷ 30）</dd>
            <dt>关键报到口径</dt><dd><b>${rate(a.keyReportRate)}</b>（规定报到日完成节点 ÷ 应到节点）</dd>
            <dt>定位活跃</dt><dd>${a.trackActiveDays} 天（近 30 天有有效定位的日历日，按对象时区计）</dd>
            <dt>越界/禁区</dt><dd>${a.breachCount30d} 次（近 30 天）</dd>
            <dt>累计训诫</dt><dd>${a.admonishCount} 次</dd>
            <dt>待处置案件</dt><dd>${a.openViolationCase
              ? '<span class="badge red">有，须先办结才能执行解除</span>'
              : '<span class="badge green">无</span>'}</dd>
            <dt>评估结论</dt><dd><b>${UI.esc(a.conclusionLabel)}</b></dd>
            <dt>鉴定意见</dt><dd id="opinion-text">${UI.esc(a.opinion || '（起草人尚未填写，可在“完善意见”里补充）')}</dd>
            <dt>生成人/时间</dt><dd>${UI.esc(a.generatedByName)} · ${UI.fmtTzFull(a.generatedAt, a.timezone)}</dd>
            ${a.approvedByName ? `<dt>审批人/时间</dt><dd>${UI.esc(a.approvedByName)} · ${UI.fmtTzFull(a.approvedAt, a.timezone)}</dd>` : ''}
            ${a.releasedAt ? `<dt>解除时间/执行人</dt><dd>${UI.fmtTzFull(a.releasedAt, a.timezone)} · ${UI.esc(a.releasedByName)}</dd>` : ''}
          </dl>
          <div style="display:flex;gap:8px;flex-wrap:wrap">
            ${can.has('UPDATE') ? '<button class="btn sm" id="btn-opinion">完善意见/结论</button>' : ''}
            ${can.has('SUBMIT') ? '<button class="btn primary sm" id="btn-submit">提交评估</button>' : ''}
            ${can.has('APPROVE') ? '<button class="btn primary sm" id="btn-approve">审批通过</button>' : ''}
            ${can.has('APPROVE') ? '<button class="btn sm danger" id="btn-reject">审批驳回</button>' : ''}
            ${can.has('EXECUTE') ? '<button class="btn danger sm" id="btn-execute">执行解除</button>' : ''}
            ${!can.size ? '<span class="confirm-warn" style="margin:0">已解除归档（终态），报告与永久标记不可变更。</span>' : ''}
          </div>
          ${a.status === 'REJECTED' ? `<div class="confirm-warn" style="margin-top:10px">该报告已被驳回，请先“完善意见/结论”后重新提交。</div>` : ''}
        </div>

        <div class="card">
          <div class="card-title">🧾 评估流程留痕</div>
          <div class="timeline">
            ${d.actions.map((x) => `
              <div class="tl-item">
                <div class="tl-line"><b>${UI.esc(x.actionLabel)}</b>
                  <span class="${STATUS_BADGE[x.statusAfter] || 'badge'}" style="margin-left:6px">${UI.esc(x.statusAfterLabel)}</span></div>
                <div class="tl-meta">${UI.esc(x.operatorName)} · ${UI.fmtTzFull(x.createdAt, a.timezone)}
                  ${x.reason ? '· ' + UI.esc(x.reason) : ''}</div>
                ${x.detail ? `<div class="tl-meta" style="color:var(--good)">↳ ${UI.esc(x.detail)}</div>` : ''}
              </div>`).join('')}
          </div>
          <div style="margin-top:10px"><button class="btn sm" id="btn-obj">查看对象档案</button></div>
        </div>`;

      mainEl.querySelector('#btn-obj').onclick = () => { location.hash = '#/objects/' + a.objectId; };
      if (can.has('UPDATE')) mainEl.querySelector('#btn-opinion').onclick = editOpinion(a, d);
      if (can.has('SUBMIT')) mainEl.querySelector('#btn-submit').onclick = submit(a);
      if (can.has('APPROVE')) mainEl.querySelector('#btn-approve').onclick = decide('approve', a, false);
      if (can.has('APPROVE')) mainEl.querySelector('#btn-reject').onclick = decide('reject', a, true);
      if (can.has('EXECUTE')) mainEl.querySelector('#btn-execute').onclick = decide('execute', a, true);
    }

    async function editOpinion(a, d) {
      const conclusions = [
        { value: 'SUGGEST_RELEASE', label: '建议按期解除' },
        { value: 'CONTINUE_EDUCATION', label: '建议延长教育' },
      ];
      const picked = await new Promise((resolve) => {
        const mask = document.createElement('div');
        mask.className = 'modal-mask';
        mask.innerHTML = `
          <div class="modal" role="dialog">
            <div class="modal-head">📝 完善评估意见</div>
            <div class="modal-body">
              <div class="field"><label>评估结论</label>
                <select class="input" id="fm-conc">
                  ${conclusions.map((c) => `<option value="${c.value}" ${a.conclusion === c.value ? 'selected' : ''}>${c.label}</option>`).join('')}
                </select></div>
              <div class="field"><label>综合鉴定意见</label>
                <textarea class="input" id="fm-op" maxlength="512" placeholder="矫正表现、教育学习、报到守纪等方面的综合鉴定">${UI.esc(a.opinion || '')}</textarea></div>
              <div class="modal-error" id="fm-err"></div>
            </div>
            <div class="modal-foot">
              <button class="btn" id="fm-cancel">取消</button>
              <button class="btn primary" id="fm-ok">保存</button>
            </div>
          </div>`;
        document.getElementById('modal-root').appendChild(mask);
        mask.querySelector('#fm-cancel').onclick = () => { mask.remove(); resolve(null); };
        mask.querySelector('#fm-ok').onclick = () => {
          const opinion = mask.querySelector('#fm-op').value.trim();
          if (opinion.length < 4) {
            const e = mask.querySelector('#fm-err'); e.style.display = 'block'; e.className = 'login-error';
            e.textContent = '鉴定意见不少于 4 个字';
            return;
          }
          mask.remove();
          resolve({ conclusion: mask.querySelector('#fm-conc').value, opinion });
        };
      });
      if (!picked) return;
      try {
        await Api.post('/release/assessments/' + a.id + '/draft', picked);
        UI.toast('意见已保存', 'success');
        await openAssessment(a.id);
      } catch (e) { UI.toast(e.message, 'error'); }
    }

    function submit(a) {
      return async () => {
        try {
          await UI.confirmModal({
            title: '提交评估报告 ' + a.reportNo, icon: '📤',
            warn: '提交后报告进入审批，不能再修改鉴定意见。',
            bodyHtml: `对象 <span class="target-name">${UI.esc(a.maskedName)}</span>，结论<b>${UI.esc(a.conclusionLabel)}</b>。`,
            confirmText: '确认提交',
          });
        } catch { return; }
        try {
          await Api.post('/release/assessments/' + a.id + '/submit', {});
          UI.toast('已提交，等待审批', 'success');
          await openAssessment(a.id);
        } catch (e) { UI.toast(e.message, 'error'); }
      };
    }

    function decide(path, a, needReason) {
      return async () => {
        const meta = {
          approve: ['审批通过', '审批通过后报告进入“已批准待执行”，由执行环节出具解除标记。', '📥', false],
          reject: ['审批驳回', '驳回须填写理由，报告退回起草人修改后可重新提交。', '↩️', true],
          execute: ['执行解除', '执行后矫正档案进入「解除」终态，出具永久解除证明书，清空并冻结实时位置，移出在矫名单与红点。', '📜', true],
        }[path];
        let reason = null;
        try {
          reason = await UI.confirmModal({
            title: meta[0] + '：' + a.reportNo, icon: meta[2], warn: meta[1],
            bodyHtml: `对象 <span class="target-name">${UI.esc(a.maskedName)}（${UI.esc(a.correctionNo)}）</span>。`,
            requireReason: path === 'reject',
            reasonLabel: '理由/说明',
            reasonPlaceholder: path === 'reject' ? '如：教育学习记录不全，退回补充' : '可留空，默认依法解除',
            confirmText: '确认' + meta[0],
            danger: meta[3],
          });
        } catch { return; }
        try {
          await Api.post('/release/assessments/' + a.id + '/' + path, { reason });
          UI.toast(meta[0] + '完成', 'success');
          await Promise.all([loadLeft(), openAssessment(a.id)]);
        } catch (e) {
          if (e.code === 'INVALID_ACTION' || e.code === 'INVALID_TRANSITION'
              || e.code === 'OPEN_CASE_EXISTS') {
            UI.alertModal('操作被拦截', `<div style="font-size:13.5px;line-height:1.7">${UI.esc(e.message)}</div>`, '⛔');
          } else { UI.toast(e.message, 'error'); }
        }
      };
    }

    await loadLeft();

    // 从对象档案跳转来打开指定评估报告
    const openAid = globalThis.__openReleaseAssessment;
    if (openAid) {
      globalThis.__openReleaseAssessment = null;
      openAssessment(openAid);
    }
  };
})(window);
