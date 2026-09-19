/* 解除与评估：到期清单 → 评估报告 → 提交/审批/退回 → 宣告解除（永久标记、定位冻结、退出在矫名单） */
(function (global) {
  const Views = global.Views || (global.Views = {});

  const RISK_LABEL = { LOW: '低风险', MEDIUM: '中风险', HIGH: '高风险' };

  Views.release = async function (root, assessmentId) {
    if (assessmentId) return renderAssessment(root, Number(assessmentId));
    return renderDue(root);
  };

  // ---------------- 到期清单 ----------------
  async function renderDue(root) {
    root.innerHTML = `<div class="skeleton">到期清单加载中…</div>`;
    const list = await Api.get('/release/due');

    function stageChip(it) {
      if (!it.hasAssessment) return '<span class="badge gray">未评估</span>';
      const map = { DRAFT: 'gray', SUBMITTED: '', APPROVED: 'green', REJECTED: 'red', DECLARED: 'green' };
      return `<span class="badge ${map[it.assessmentStage] || ''}">${UI.esc(it.assessmentStageLabel)}</span>`;
    }

    root.innerHTML = `
      <div class="page-head"><h2>📭 解除与评估</h2>
        <div class="desc">矫正期满对象先生成<b>解除评估报告</b>，走完 提交→审批→宣告解除 后打<b>永久解除标记</b>：
          退出在矫名单与作战台红点、定位数据停止更新，档案仍可按矫正编号查询。</div>
      </div>
      <div class="card">
        <div class="card-title">📅 矫正到期清单
          <span class="sub">未来 30 天内到期或已在办理解除的对象</span>
        </div>
        <div class="table-wrap">
          ${list.length ? `
          <table class="data">
            <thead><tr><th>矫正编号</th><th>对象</th><th>司法所</th><th>档案状态</th>
              <th>矫正期满</th><th>剩余/逾期</th><th>评估流程</th><th></th></tr></thead>
            <tbody>
              ${list.map((it) => `
                <tr>
                  <td>${UI.esc(it.correctionNo)}</td>
                  <td><b>${UI.esc(it.maskedName)}</b></td>
                  <td>${UI.esc(it.officeName)}</td>
                  <td>${UI.esc(it.statusLabel)}</td>
                  <td style="white-space:nowrap">${UI.esc(it.endDate || '—')}</td>
                  <td>${it.overdue
                    ? `<span class="badge red">已逾期 ${-it.daysUntilExpiry} 天</span>`
                    : `<span class="badge ${it.daysUntilExpiry <= 7 ? 'red' : 'gray'}">剩 ${it.daysUntilExpiry} 天</span>`}</td>
                  <td>${stageChip(it)}</td>
                  <td>${it.hasAssessment
                    ? `<button class="btn sm primary" data-continue="${it.correctionNo}">办理</button>`
                    : `<button class="btn sm" data-gen="${it.objectId}">生成评估报告</button>`}</td>
                </tr>`).join('')}
            </tbody>
          </table>`
          : `<div class="state-box"><div class="ico">📅</div><h3>近期无到期对象</h3>
            <p>未来 30 天内没有矫正期满、需要办理解除评估的在管对象。</p></div>`}
        </div>
      </div>`;

    root.querySelectorAll('[data-gen]').forEach((b) => {
      b.onclick = async () => {
        try {
          const a = await Api.post('/release/assessments', { objectId: Number(b.dataset.gen) });
          UI.toast('已生成评估报告（草稿）', 'success');
          location.hash = '#/release/assessments/' + a.id;
        } catch (e) { UI.toast(e.message, 'error'); }
      };
    });
    root.querySelectorAll('[data-continue]').forEach((b) => {
      b.onclick = async () => {
        // 通过对象编号找最新评估 id
        const no = b.dataset.continue;
        const a = await Api.get('/release/objects/' + findIdByNo(list, no) + '/latest');
        if (a && a.id) location.hash = '#/release/assessments/' + a.id;
        else UI.toast('评估记录获取失败', 'error');
      };
    });
  }

  function findIdByNo(list, no) {
    return (list.find((x) => x.correctionNo === no) || {}).objectId;
  }

  // ---------------- 评估报告详情 ----------------
  async function renderAssessment(root, id) {
    root.innerHTML = `<div class="skeleton">评估报告加载中…</div>`;
    let a;
    async function reload() { a = await Api.get('/release/assessments/' + id); draw(); }

    function draw() {
      const tz = a.timezone;
      const editable = a.stage === 'DRAFT' || a.stage === 'REJECTED';
      root.innerHTML = `
        <div class="page-head" style="display:flex;align-items:center;gap:12px;flex-wrap:wrap">
          <button class="btn sm" id="btn-back">← 到期清单</button>
          <h2 style="margin:0">解除矫正评估报告</h2>
          <span style="color:var(--ink-muted);font-size:13px">${UI.esc(a.assessmentNo)}</span>
          ${stageBadge(a.stage)}
          <span style="flex:1"></span>
          <button class="btn sm" id="btn-object">档案 ${UI.esc(a.correctionNo)}</button>
        </div>

        ${a.stage === 'DECLARED' ? permanentBanner(a, tz) : ''}

        <div class="detail-grid">
          <div>
            <div class="card">
              <div class="card-title">📄 对象与期限</div>
              <dl class="kv">
                <dt>对象</dt><dd><b>${UI.esc(a.maskedName)}</b>（${UI.esc(a.correctionNo)}）· ${UI.esc(a.officeName)}</dd>
                <dt>矫正期满</dt><dd>${UI.esc(a.endDate || '—')}
                  ${a.overdue ? '<span class="badge red" style="margin-left:6px">已逾期 ' + (-a.daysUntilExpiry) + ' 天</span>'
                    : '<span class="badge gray" style="margin-left:6px">剩 ' + a.daysUntilExpiry + ' 天</span>'}</dd>
                <dt>评估人</dt><dd>${UI.esc(a.assessorName || '—')}${a.assessedAt ? ' · ' + UI.fmtTzFull(a.assessedAt, tz) : ''}</dd>
              </dl>
            </div>

            <div class="card">
              <div class="card-title">📝 评估内容${editable ? '<span class="sub">可编辑，保存后提交</span>' : ''}</div>
              ${assessmentForm(a, editable)}
              ${editable ? `<div style="margin-top:10px"><button class="btn primary" id="btn-save">💾 保存报告</button></div>` : ''}
            </div>

            ${completionHtml(a.completion)}
          </div>

          <div>
            <div class="card">
              <div class="card-title">🔁 解除流程</div>
              ${flowPanel(a)}
            </div>
            <div class="card">
              <div class="card-title">🧾 解除留痕</div>
              <div class="timeline">
                ${a.logs.map((l) => `
                  <div class="tl-item">
                    <div class="tl-line"><span class="badge gray">${UI.esc(l.actionLabel)}</span> → <b>${UI.esc(l.toStageLabel)}</b></div>
                    <div class="tl-meta">${UI.esc(l.operatorName)} · ${UI.fmtTzFull(l.operatedAt, tz)}
                      ${l.reason ? '· ' + UI.esc(l.reason) : ''}</div>
                    ${l.detail ? `<div class="tl-meta" style="color:var(--ink-muted)">${UI.esc(l.detail)}</div>` : ''}
                  </div>`).join('')}
              </div>
            </div>
          </div>
        </div>`;

      root.querySelector('#btn-back').onclick = () => { location.hash = '#/release'; };
      root.querySelector('#btn-object').onclick = () => { location.hash = '#/objects/' + a.objectId; };
      if (editable) root.querySelector('#btn-save').onclick = onSave;
      bindFlow();
    }

    function stageBadge(stage) {
      const map = { DRAFT: 'gray', SUBMITTED: '', APPROVED: 'green', REJECTED: 'red', DECLARED: 'green' };
      const label = { DRAFT: '评估草稿', SUBMITTED: '待审批', APPROVED: '待宣告', REJECTED: '退回补正', DECLARED: '已宣告解除' };
      return `<span class="badge ${map[stage] || ''}">${label[stage]}</span>`;
    }

    function permanentBanner(x, tz) {
      return `
        <div class="card" style="border-left:4px solid var(--good)">
          <div style="display:flex;gap:12px;align-items:flex-start;flex-wrap:wrap">
            <div style="font-size:26px">📭</div>
            <div style="flex:1;min-width:260px">
              <div style="font-weight:700;color:var(--good)">已宣告解除并打上永久标记（不可逆）</div>
              <div style="font-size:13px;color:var(--ink-secondary);margin-top:4px">
                宣告人 ${UI.esc(x.declaredByName || '—')} · ${x.declaredAt ? UI.fmtTzFull(x.declaredAt, tz) : '—'}（${UI.esc(tz)}）<br/>
                解除证明书编号：<b>${UI.esc(x.certificateNo || '—')}</b><br/>
                该对象已退出在矫名单与作战台红点，定位数据不再实时更新；档案按矫正编号归档可查。
              </div>
            </div>
          </div>
        </div>`;
    }

    function assessmentForm(x, editable) {
      const dis = editable ? '' : 'disabled';
      const risk = x.riskLevel || 'LOW';
      return `
        <div class="field"><label>综合考核评分（0-100）</label>
          <input class="input" id="f-score" type="number" min="0" max="100" value="${x.score == null ? '' : x.score}" ${dis}/></div>
        <div class="field" style="margin-top:8px"><label>教育学习情况</label>
          <textarea class="input" id="f-education" maxlength="512" ${dis}>${UI.esc(x.education || '')}</textarea></div>
        <div class="field" style="margin-top:8px"><label>日常监管/报到/定位合规</label>
          <textarea class="input" id="f-compliance" maxlength="512" ${dis}>${UI.esc(x.compliance || '')}</textarea></div>
        <div class="field" style="margin-top:8px"><label>认罪悔罪与思想动态</label>
          <textarea class="input" id="f-repentance" maxlength="512" ${dis}>${UI.esc(x.repentance || '')}</textarea></div>
        <div class="field" style="margin-top:8px"><label>再犯罪风险评估</label>
          <select class="input" id="f-risk" ${dis}>
            ${['LOW', 'MEDIUM', 'HIGH'].map((r) => `<option value="${r}" ${r === risk ? 'selected' : ''}>${RISK_LABEL[r]}</option>`).join('')}
          </select></div>
        <div class="field" style="margin-top:8px"><label>评估结论与建议</label>
          <textarea class="input" id="f-conclusion" maxlength="512" ${dis}>${UI.esc(x.conclusion || '')}</textarea></div>
        ${x.approvalOpinion ? `<div class="confirm-warn" style="margin-top:10px">审批意见：${UI.esc(x.approvalOpinion)}
          （${UI.esc(x.approvedByName || '')}）</div>` : ''}`;
    }

    function flowPanel(x) {
      const Btn = (id, label, cls) => `<button class="btn ${cls || 'primary'} sm" id="${id}" style="margin:4px 6px 0 0">${label}</button>`;
      switch (x.stage) {
        case 'DRAFT':
          return `<div style="font-size:13px;color:var(--ink-secondary)">报告为草稿，补全后提交审批。</div>`
            + Btn('btn-submit', '提交审批');
        case 'REJECTED':
          return `<div class="confirm-warn">评估被退回补正，请根据审批意见修改后重新提交。</div>`
            + Btn('btn-submit', '重新提交');
        case 'SUBMITTED':
          return `<div style="font-size:13px;color:var(--ink-secondary)">已提交，等待审批。</div>`
            + Btn('btn-approve', '审批通过') + Btn('btn-return', '退回补正');
        case 'APPROVED':
          return `<div style="font-size:13px;color:var(--ink-secondary)">审批已通过。宣告解除将打永久标记、冻结定位并退出在矫名单。</div>`
            + Btn('btn-declare', '宣告解除（永久）', 'danger');
        case 'DECLARED':
          return `<div class="confirm-warn" style="color:var(--good)">已宣告解除，流程终结，永久标记不可逆。</div>`;
        default:
          return '';
      }
    }

    function bindFlow() {
      const bind = (elId, fn) => { const el = root.querySelector('#' + elId); if (el) el.onclick = fn; };
      bind('btn-submit', () => reasonAct('提交解除评估审批', '提交',
        '/submit', { body: {} }, false));
      bind('btn-approve', () => opinionAct('审批通过', 'approve', '审批意见'));
      bind('btn-return', () => opinionAct('退回补正', 'return', '退回意见'));
      bind('btn-declare', () => declareAct());
    }

    function readForm() {
      const scoreV = root.querySelector('#f-score').value;
      return {
        score: scoreV === '' ? null : Number(scoreV),
        education: root.querySelector('#f-education').value.trim(),
        compliance: root.querySelector('#f-compliance').value.trim(),
        repentance: root.querySelector('#f-repentance').value.trim(),
        riskLevel: root.querySelector('#f-risk').value,
        conclusion: root.querySelector('#f-conclusion').value.trim(),
      };
    }

    async function onSave() {
      try {
        await requestPut('/release/assessments/' + id, readForm());
        UI.toast('评估报告已保存', 'success');
        await reload();
      } catch (e) { UI.toast(e.message, 'error'); }
    }

    // Api 仅封装了 get/post，这里补一个 PUT
    function requestPut(path, body) {
      return Api.request('PUT', path, body);
    }

    async function reasonAct(title, label, suffix, payload, danger) {
      let reason = null;
      try {
        reason = await UI.confirmModal({
          title, icon: '📭',
          warn: '该操作写入解除流程留痕，记录操作人与意见。',
          bodyHtml: `<span class="target-name">${UI.esc(a.correctionNo)}</span> 的解除评估报告`,
          requireReason: false, confirmText: label,
        });
      } catch { return; }
      try {
        await Api.post('/release/assessments/' + id + suffix, { reason: reason || '' });
        UI.toast(label + '成功', 'success');
        await reload();
      } catch (e) { handleErr(e); }
    }

    async function opinionAct(label, suffix, opinionLabel) {
      let opinion;
      try {
        opinion = await UI.confirmModal({
          title: label, icon: '📝',
          warn: '审批意见将留痕；退回后评估人需补正并重新提交。',
          bodyHtml: `<span class="target-name">${UI.esc(a.correctionNo)}</span> 的解除评估报告`,
          reasonLabel: opinionLabel, reasonPlaceholder: '请填写审批意见（不少于 4 字）',
          requireReason: true, confirmText: label,
        });
      } catch { return; }
      try {
        await Api.post('/release/assessments/' + id + '/' + suffix, { reason: opinion });
        UI.toast('已' + label, 'success');
        await reload();
      } catch (e) { handleErr(e); }
    }

    async function declareAct() {
      let reason;
      try {
        reason = await UI.confirmModal({
          title: '宣告解除（永久）', icon: '📭', danger: true,
          warn: '宣告后对象打永久解除标记（不可逆）：退出在矫名单与作战台红点、定位数据停止更新；档案按编号归档可查。宣告前档案须处于「在矫」。',
          bodyHtml: `<span class="target-name">${UI.esc(a.maskedName)}（${UI.esc(a.correctionNo)}）</span> 矫正期满，依法宣告解除社区矫正。`,
          reasonLabel: '宣告意见', reasonPlaceholder: '矫正期满、考核合格，依法宣告解除（不少于 4 字）',
          requireReason: true, confirmText: '确认宣告解除', danger: true,
        });
      } catch { return; }
      try {
        await Api.post('/release/assessments/' + id + '/declare', { reason });
        UI.toast('已宣告解除并打永久标记', 'success');
        await reload();
      } catch (e) { handleErr(e); }
    }

    function handleErr(e) {
      if (e.code === 'RELEASE_TRANSITION' || e.code === 'INVALID_TRANSITION'
          || e.code === 'ASSESSMENT_INCOMPLETE' || e.code === 'OPINION_REQUIRED') {
        UI.alertModal('操作被系统拦截',
          `<div style="font-size:13.5px;line-height:1.7">${UI.esc(e.message)}</div>`, '⛔');
      } else UI.toast(e.message, 'error');
    }

    function completionHtml(c) {
      if (!c) return '';
      const pct = (x) => Math.round(x * 100) + '%';
      return `
        <div class="card completion-card">
          <div class="card-title">📊 近 30 天考核（评估参考）
            <span class="sub">${UI.esc(c.basisFrom)} ～ ${UI.esc(c.basisTo)} · ${UI.esc(c.timezone)}</span>
          </div>
          ${c.opposite ? '<div class="opposite-warn">⚠ 打卡天数与关键报到两口径差异较大，请结合两口径综合评估。</div>' : ''}
          <div class="completion-grid">
            <div class="completion-tile">
              <div class="ct-label">口径一 · 打卡天数</div>
              <div class="ct-num">${pct(c.checkinDayRate)}</div>
              <div class="ct-frac">${c.actualCheckinDays} / ${c.calendarDays} 天</div>
              <div class="ct-def">${UI.esc(c.checkinDayDefinition)}</div>
            </div>
            <div class="completion-tile">
              <div class="ct-label">口径二 · 关键报到</div>
              <div class="ct-num">${pct(c.keyReportRate)}</div>
              <div class="ct-frac">${c.keyReportDone} / ${c.keyReportDue} 节点</div>
              <div class="ct-def">${UI.esc(c.keyReportDefinition)}</div>
            </div>
          </div>
        </div>`;
    }

    await reload();
  }
})(window);
