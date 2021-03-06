import QuerySettings from './RightBar/SettingsTab/QuerySettings';
import { LOAD_ATTRIBUTES, LOAD_NEIGHBOURS } from '../shared/StoresActions';

export default {
  registerHandlers({ state, dispatch, commit }) {
    commit('registerCanvasEvent', {
      event: 'selectNode',
      callback: (params) => {
        commit('selectedNodes', params.nodes);
        commit('setContextMenu', { show: false, x: null, y: null });
      },
    });

    commit('registerCanvasEvent', {
      event: 'dragStart',
      callback: (params) => {
        if (!params.nodes.length > 1) {
          commit('selectedNodes', [params.nodes[0]]);
        }
      },
    });

    commit('registerCanvasEvent', {
      event: 'click',
      callback: (params) => {
        if (!params.nodes.length) { commit('selectedNodes', null); }
        commit('setContextMenu', { show: false, x: null, y: null });
      },
    });

    commit('registerCanvasEvent', {
      event: 'oncontext',
      callback: (params) => {
        const nodeId = state.visFacade.getNetwork().getNodeAt(params.pointer.DOM);
        if (nodeId) {
          if (!(params.nodes.length > 1)) {
            state.visFacade.getNetwork().unselectAll();
            commit('selectedNodes', [nodeId]);
            state.visFacade.getNetwork().selectNodes([nodeId]);
          }
        } else if (!(params.nodes.length > 1)) {
          commit('selectedNodes', null);
          state.visFacade.getNetwork().unselectAll();
        }
      },
    });

    commit('registerCanvasEvent', {
      event: 'oncontext',
      callback: (params) => {
        // Show context menu when keyspace is selected and canvas has data
        if (state.currentKeyspace && (state.canvasData.entities || state.canvasData.attributes || state.canvasData.relationships)) {
          commit('setContextMenu', { show: true, x: params.pointer.DOM.x, y: params.pointer.DOM.y });
        }
      },
    });

    commit('registerCanvasEvent', {
      event: 'doubleClick',
      callback: async (params) => {
        const nodeId = params.nodes[0];
        if (!nodeId) return;

        const neighboursLimit = QuerySettings.getNeighboursLimit();
        const visNode = state.visFacade.getNode(nodeId);
        const action = (params.event.srcEvent.shiftKey) ? LOAD_ATTRIBUTES : LOAD_NEIGHBOURS;
        dispatch(action, { visNode, neighboursLimit });
      },
    });

    commit('registerCanvasEvent', {
      event: 'deselectNode',
      callback: () => {
        commit('setContextMenu', { show: false, x: null, y: null });
      },
    });

    commit('registerCanvasEvent', {
      event: 'dragStart',
      callback: () => {
        commit('setContextMenu', { show: false, x: null, y: null });
      },
    });

    commit('registerCanvasEvent', {
      event: 'zoom',
      callback: () => {
        commit('setContextMenu', { show: false, x: null, y: null });
      },
    });

    commit('registerCanvasEvent', {
      event: 'hold',
      callback: (params) => {
        if (params.nodes.length) { commit('selectedNodes', null); state.visFacade.getNetwork().unselectAll(); }
      },
    });
  },
};
