/*
 * Grakn - A Distributed Semantic Database
 * Copyright (C) 2016  Grakn Labs Limited
 *
 * Grakn is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Grakn is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Grakn. If not, see <http://www.gnu.org/licenses/gpl.txt>.
 */
package ai.grakn.graql.internal.reasoner.atom.binary;

import ai.grakn.GraknTx;
import ai.grakn.concept.Label;
import ai.grakn.concept.RelationshipType;
import ai.grakn.concept.Role;
import ai.grakn.concept.SchemaConcept;
import ai.grakn.concept.Type;
import ai.grakn.exception.GraqlQueryException;
import ai.grakn.graql.Graql;
import ai.grakn.graql.Var;
import ai.grakn.graql.VarPattern;
import ai.grakn.graql.admin.Answer;
import ai.grakn.graql.admin.Atomic;
import ai.grakn.graql.admin.MultiUnifier;
import ai.grakn.graql.admin.PatternAdmin;
import ai.grakn.graql.admin.ReasonerQuery;
import ai.grakn.graql.admin.RelationPlayer;
import ai.grakn.graql.admin.Unifier;
import ai.grakn.graql.admin.VarPatternAdmin;
import ai.grakn.graql.internal.pattern.property.IsaProperty;
import ai.grakn.graql.internal.pattern.property.RelationshipProperty;
import ai.grakn.graql.internal.query.QueryAnswer;
import ai.grakn.graql.internal.reasoner.MultiUnifierImpl;
import ai.grakn.graql.internal.reasoner.ResolutionPlan;
import ai.grakn.graql.internal.reasoner.UnifierImpl;
import ai.grakn.graql.internal.reasoner.atom.Atom;
import ai.grakn.graql.internal.reasoner.atom.binary.type.IsaAtom;
import ai.grakn.graql.internal.reasoner.atom.predicate.IdPredicate;
import ai.grakn.graql.internal.reasoner.atom.predicate.Predicate;
import ai.grakn.graql.internal.reasoner.query.ReasonerQueryImpl;
import ai.grakn.graql.internal.reasoner.utils.ReasonerUtils;
import ai.grakn.graql.internal.reasoner.utils.conversion.RoleTypeConverter;
import ai.grakn.graql.internal.reasoner.utils.conversion.SchemaConceptConverterImpl;
import ai.grakn.util.CommonUtil;
import ai.grakn.util.ErrorMessage;
import ai.grakn.util.Schema;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import ai.grakn.graql.internal.reasoner.utils.Pair;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ai.grakn.graql.internal.reasoner.utils.ReasonerUtils.areDisjointTypes;
import static ai.grakn.graql.internal.reasoner.utils.ReasonerUtils.getCompatibleRelationTypesWithRoles;
import static ai.grakn.graql.internal.reasoner.utils.ReasonerUtils.getSupers;
import static ai.grakn.graql.internal.reasoner.utils.ReasonerUtils.multimapIntersection;
import static ai.grakn.graql.internal.reasoner.utils.ReasonerUtils.playableRoles;
import static java.util.stream.Collectors.toSet;

/**
 *
 * <p>
 * Atom implementation defining a relation atom corresponding to a combined {@link RelationshipProperty}
 * and (optional) {@link IsaProperty}. The relation atom is a {@link TypeAtom} with relationship players.
 * </p>
 *
 * @author Kasper Piskorski
 *
 */
public class RelationshipAtom extends IsaAtom {

    private int hashCode = 0;
    private Multimap<Role, Var> roleVarMap = null;
    private Multimap<Role, SchemaConcept> roleTypeMap = null;
    private Multimap<Role, String> roleConceptIdMap = null;
    private final ImmutableList<RelationPlayer> relationPlayers;
    private final Set<Label> roleLabels;

    public RelationshipAtom(VarPatternAdmin pattern, Var predicateVar, @Nullable IdPredicate predicate, ReasonerQuery par) {
        super(pattern, predicateVar, predicate, par);
        List<RelationPlayer> rps = new ArrayList<>();
        getPattern().asVarPattern()
                .getProperty(RelationshipProperty.class)
                .ifPresent(prop -> prop.relationPlayers().forEach(rps::add));
        this.relationPlayers = ImmutableList.copyOf(rps);
        this.roleLabels = relationPlayers.stream()
                .map(RelationPlayer::getRole)
                .flatMap(CommonUtil::optionalToStream)
                .map(VarPatternAdmin::getTypeLabel)
                .flatMap(CommonUtil::optionalToStream)
                .collect(toSet());
    }

    private RelationshipAtom(RelationshipAtom a) {
        super(a);
        this.relationPlayers = a.relationPlayers;
        this.roleLabels = a.roleLabels;
        this.roleVarMap = a.roleVarMap;
    }

    @Override
    public RelationshipAtom toRelationshipAtom(){ return this;}

    @Override
    public String toString(){
        String relationString = (isUserDefined()? getVarName() + " ": "") +
                (getSchemaConcept() != null? getSchemaConcept().getLabel() : "") +
                getRelationPlayers().toString();
        return relationString + getPredicates(IdPredicate.class).map(IdPredicate::toString).collect(Collectors.joining(""));
    }
    
    private Set<Label> getRoleLabels() { return roleLabels;}
    private ImmutableList<RelationPlayer> getRelationPlayers() { return relationPlayers;}

    /**
     * @return set constituting the role player var names
     */
    public Set<Var> getRolePlayers() {
        return getRelationPlayers().stream().map(c -> c.getRolePlayer().var()).collect(toSet());
    }

    private Set<Var> getRoleVariables(){
        return getRelationPlayers().stream()
                .map(RelationPlayer::getRole)
                .flatMap(CommonUtil::optionalToStream)
                .map(VarPatternAdmin::var)
                .filter(Var::isUserDefinedName)
                .collect(Collectors.toSet());
    }

    @Override
    public Atomic copy() {
        return new RelationshipAtom(this);
    }

    /**
     * construct a $varName (rolemap) isa $typeVariable relation
     * @param varName            variable name
     * @param typeVariable       type variable name
     * @param rolePlayerMappings list of rolePlayer-roleType mappings
     * @return corresponding {@link VarPatternAdmin}
     */
    private static VarPatternAdmin constructRelationVarPattern(Var varName, Var typeVariable, List<Pair<Var, VarPattern>> rolePlayerMappings) {
        VarPattern var = !varName.getValue().isEmpty()? varName : Graql.var();
        for (Pair<Var, VarPattern> mapping : rolePlayerMappings) {
            Var rp = mapping.getKey();
            VarPattern role = mapping.getValue();
            var = role == null? var.rel(rp) : var.rel(role, rp);
        }
        if (!typeVariable.getValue().isEmpty()) var = var.isa(typeVariable);
        return var.admin();
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = 1;
            hashCode = hashCode * 37 + (getTypeId() != null ? getTypeId().hashCode() : 0);
            hashCode = hashCode * 37 + getVarNames().hashCode();
        }
        return hashCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || this.getClass() != obj.getClass()) return false;
        if (obj == this) return true;
        RelationshipAtom a2 = (RelationshipAtom) obj;
        return Objects.equals(this.getTypeId(), a2.getTypeId())
                && getVarNames().equals(a2.getVarNames())
                && getRelationPlayers().equals(a2.getRelationPlayers());
    }

    @Override
    public boolean isEquivalent(Object obj) {
        if (obj == null || this.getClass() != obj.getClass()) return false;
        if (obj == this) return true;
        RelationshipAtom a2 = (RelationshipAtom) obj;
        return (isUserDefined() == a2.isUserDefined())
                && Objects.equals(this.getTypeId(), a2.getTypeId())
                //check relation players equivalent
                && getRolePlayers().size() == a2.getRolePlayers().size()
                && getRelationPlayers().size() == a2.getRelationPlayers().size()
                && getRoleLabels().equals(a2.getRoleLabels())
                //check bindings
                && getRoleConceptIdMap().equals(a2.getRoleConceptIdMap())
                && getRoleTypeMap().equals(a2.getRoleTypeMap());
    }

    @Override
    public int equivalenceHashCode() {
        int equivalenceHashCode = 1;
        equivalenceHashCode = equivalenceHashCode * 37 + (this.getTypeId() != null ? this.getTypeId().hashCode() : 0);
        equivalenceHashCode = equivalenceHashCode * 37 + this.getRoleConceptIdMap().hashCode();
        equivalenceHashCode = equivalenceHashCode * 37 + this.getRoleTypeMap().hashCode();
        equivalenceHashCode = equivalenceHashCode * 37 + this.getRoleLabels().hashCode();
        return equivalenceHashCode;
    }

    @Override
    public boolean isRelation() {
        return true;
    }

    @Override
    public boolean isSelectable() {
        return true;
    }

    @Override
    public boolean isType() {
        return getSchemaConcept() != null;
    }

    @Override
    public boolean requiresMaterialisation() {
        return isUserDefined();
    }

    @Override
    public boolean requiresRoleExpansion() {
        return !getRoleVariables().isEmpty();
    }

    @Override
    public boolean isAllowedToFormRuleHead(){
        //can form a rule head if specified type, type is not implicit and all relation players have a specified/non-implicit/unambiguously inferrable role type
        return getSchemaConcept() != null
                && !getSchemaConcept().asType().isImplicit()
                && !hasMetaRoles()
                && !hasImplicitRoles();
    }

    /**
     * @return true if any of the relation's roles are meta roles
     */
    private boolean hasMetaRoles(){
        return roleLabels.stream().filter(Schema.MetaSchema::isMetaLabel).findFirst().isPresent();
    }

    /**
     * @return true if any of the relation's roles are implicit roles
     */
    private boolean hasImplicitRoles(){
        return getRoleVarMap().keySet().stream().filter(SchemaConcept::isImplicit).findFirst().isPresent();
    }

    @Override
    public Set<String> validateOntologically() {
        Set<String> errors = new HashSet<>();
        SchemaConcept type = getSchemaConcept();
        if (type != null && !type.isRelationshipType()){
            errors.add(ErrorMessage.VALIDATION_RULE_INVALID_RELATION_TYPE.getMessage(type.getLabel()));
            return errors;
        }

        //check roles are ok
        Map<Var, SchemaConcept> varSchemaConceptMap = getParentQuery().getVarSchemaConceptMap();

        for (Map.Entry<Role, Collection<Var>> e : getRoleVarMap().asMap().entrySet() ){
            Role role = e.getKey();
            if (!Schema.MetaSchema.isMetaLabel(role.getLabel())) {
                //check whether this role can be played in this relation
                if (type != null && type.asRelationshipType().relates().noneMatch(r -> r.equals(role))) {
                    errors.add(ErrorMessage.VALIDATION_RULE_ROLE_CANNOT_BE_PLAYED.getMessage(role.getLabel(), type.getLabel()));
                }

                //check whether the role player's type allows playing this role
                for (Var player : e.getValue()) {
                    SchemaConcept playerType = varSchemaConceptMap.get(player);
                    if (playerType != null && playerType.asType().plays().noneMatch(plays -> plays.equals(role))) {
                        errors.add(ErrorMessage.VALIDATION_RULE_TYPE_CANNOT_PLAY_ROLE.getMessage(playerType.getLabel(), role.getLabel(), type == null? "" : type.getLabel()));
                    }
                }
            }
        }
        return errors;
    }

    @Override
    public int computePriority(Set<Var> subbedVars) {
        int priority = super.computePriority(subbedVars);
        priority += ResolutionPlan.IS_RELATION_ATOM;
        return priority;
    }

    @Override
    public Stream<IdPredicate> getPartialSubstitutions() {
        Set<Var> rolePlayers = getRolePlayers();
        return getPredicates(IdPredicate.class)
                .filter(pred -> rolePlayers.contains(pred.getVarName()));
    }

    public Stream<IdPredicate> getRolePredicates(){
        return getRelationPlayers().stream()
                .map(RelationPlayer::getRole)
                .flatMap(CommonUtil::optionalToStream)
                .filter(var -> var.var().isUserDefinedName())
                .filter(vp -> vp.getTypeLabel().isPresent())
                .map(vp -> {
                    Label label = vp.getTypeLabel().orElse(null);
                    return new IdPredicate(vp.var(), tx().getRole(label.getValue()), getParentQuery());
                });
    }

    /**
     * @return map of pairs role type - Id predicate describing the role player playing this role (substitution)
     */
    private Multimap<Role, String> getRoleConceptIdMap() {
        if (roleConceptIdMap == null) {
            roleConceptIdMap = ArrayListMultimap.create();

            Map<Var, IdPredicate> varSubMap = getPartialSubstitutions()
                    .collect(Collectors.toMap(Atomic::getVarName, pred -> pred));
            Multimap<Role, Var> roleMap = getRoleVarMap();

            roleMap.entries().stream()
                    .filter(e -> varSubMap.containsKey(e.getValue()))
                    .sorted(Comparator.comparing(e -> varSubMap.get(e.getValue()).getPredicateValue()))
                    .forEach(e -> roleConceptIdMap.put(e.getKey(), varSubMap.get(e.getValue()).getPredicateValue()));
        }
        return roleConceptIdMap;
    }

    private Multimap<Role, SchemaConcept> getRoleTypeMap() {
        if (roleTypeMap == null) {
            roleTypeMap = ArrayListMultimap.create();
            Multimap<Role, Var> roleMap = getRoleVarMap();
            Map<Var, SchemaConcept> varTypeMap = getParentQuery().getVarSchemaConceptMap();

            roleMap.entries().stream()
                    .filter(e -> varTypeMap.containsKey(e.getValue()))
                    .sorted(Comparator.comparing(e -> varTypeMap.get(e.getValue()).getLabel()))
                    .forEach(e -> roleTypeMap.put(e.getKey(), varTypeMap.get(e.getValue())));
        }
        return roleTypeMap;
    }
    
    @Override
    public boolean isRuleApplicableViaAtom(Atom ruleAtom) {
        if(ruleAtom.isResource()) return isRuleApplicableViaAtom(ruleAtom.toRelationshipAtom());
        //findbugs complains about cast without it
        if (!(ruleAtom instanceof RelationshipAtom)) return false;
        
        RelationshipAtom headAtom = (RelationshipAtom) ruleAtom;
        RelationshipAtom atomWithType = this.addType(headAtom.getSchemaConcept()).inferRoles(new QueryAnswer());

        //rule head atom is applicable if it is unifiable
        return headAtom.getRelationPlayers().size() >= atomWithType.getRelationPlayers().size()
                && !headAtom.getRelationPlayerMappings(atomWithType).isEmpty();
    }

    private Set<Role> getExplicitRoleTypes() {
        Set<Role> roles = new HashSet<>();
        ReasonerQueryImpl parent = (ReasonerQueryImpl) getParentQuery();
        GraknTx graph = parent.tx();

        Set<VarPatternAdmin> roleVars = getRelationPlayers().stream()
                .map(RelationPlayer::getRole)
                .flatMap(CommonUtil::optionalToStream)
                .collect(Collectors.toSet());
        //try directly
        roleVars.stream()
                .map(VarPatternAdmin::getTypeLabel)
                .flatMap(CommonUtil::optionalToStream)
                .map(graph::<Role>getSchemaConcept)
                .forEach(roles::add);

        //try indirectly
        roleVars.stream()
                .filter(v -> v.var().isUserDefinedName())
                .map(VarPatternAdmin::var)
                .map(this::getIdPredicate)
                .filter(Objects::nonNull)
                .map(Predicate::getPredicate)
                .map(graph::<Role>getConcept)
                .forEach(roles::add);
        return roles;
    }

    public RelationshipAtom addType(SchemaConcept type) {
        Pair<VarPatternAdmin, IdPredicate> typedPair = getTypedPair(type);
        return new RelationshipAtom(typedPair.getKey(), typedPair.getValue().getVarName(), typedPair.getValue(), this.getParentQuery());
    }

    /**
     * @param sub answer
     * @return entity types inferred from answer entity information
     */
    private Set<Pair<Var, Type>> inferEntityTypes(Answer sub) {
        if (sub.isEmpty()) return Collections.emptySet();
        //Answer mergedSub = this.getParentQuery().getSubstitution().merge(sub);

        Set<Var> subbedVars = Sets.intersection(getRolePlayers(), sub.vars());
        Set<Var> untypedVars = Sets.difference(subbedVars, getParentQuery().getVarSchemaConceptMap().keySet());
        return untypedVars.stream()
                .map(v -> new Pair<>(v, sub.get(v)))
                .filter(p -> p.getValue().isThing())
                .map(e -> new Pair<>(e.getKey(), e.getValue().asThing().type()))
                .collect(toSet());
    }

    /**
     * infer relation types that this relationship atom can potentially have
     * NB: entity types and role types are treated separately as they behave differently:
     * entity types only play the explicitly defined roles (not the relevant part of the hierarchy of the specified role)
     * @return list of relation types this atom can have ordered by the number of compatible role types
     */
    public List<RelationshipType> inferPossibleRelationTypes(Answer sub) {
        if (getSchemaConcept() != null) return Collections.singletonList(getSchemaConcept().asRelationshipType());

        //look at available role types
        Multimap<RelationshipType, Role> compatibleTypesFromRoles = getCompatibleRelationTypesWithRoles(getExplicitRoleTypes(), new RoleTypeConverter());

        //look at entity types
        Map<Var, SchemaConcept> varTypeMap = getParentQuery().getVarSchemaConceptMap();

        //explicit types
        Set<SchemaConcept> types = getRolePlayers().stream()
                .filter(varTypeMap::containsKey)
                .map(varTypeMap::get)
                .collect(toSet());

        //types deduced from substitution
        inferEntityTypes(sub).stream().map(Pair::getValue).forEach(types::add);

        Multimap<RelationshipType, Role> compatibleTypesFromTypes = getCompatibleRelationTypesWithRoles(types, new SchemaConceptConverterImpl());

        Multimap<RelationshipType, Role> compatibleTypes;
        //intersect relation types from roles and types
        if (compatibleTypesFromRoles.isEmpty()){
            compatibleTypes = compatibleTypesFromTypes;
        } else if (!compatibleTypesFromTypes.isEmpty()){
            compatibleTypes = multimapIntersection(compatibleTypesFromTypes, compatibleTypesFromRoles);
        } else {
            compatibleTypes = compatibleTypesFromRoles;
        }

        return compatibleTypes.asMap().entrySet().stream()
                .sorted(Comparator.comparing(e -> -e.getValue().size()))
                .map(Map.Entry::getKey)
                .filter(t -> Sets.intersection(getSupers(t), compatibleTypes.keySet()).isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * attempt to infer the relation type of this relationship
     * @param sub extra instance information to aid entity type inference
     * @return either this if relation type can't be inferred or a fresh relationship with inferred relationship type
     */
    private RelationshipAtom inferRelationshipType(Answer sub){
        if (getTypePredicate() != null) return this;

        List<RelationshipType> relationshipTypes = inferPossibleRelationTypes(sub);
        if (relationshipTypes.size() == 1){
            return addType(relationshipTypes.iterator().next());
        } else {
            return this;
        }
    }

    @Override
    public RelationshipAtom inferTypes() {
        return this
                .inferRelationshipType(new QueryAnswer())
                .inferRoles(new QueryAnswer());
    }

    @Override
    public List<Atom> atomOptions(Answer sub) {
        return this.inferPossibleRelationTypes(sub).stream()
                .map(this::addType)
                .map(at -> at.inferRoles(sub))
                //order by number of distinct roles
                .sorted(Comparator.comparing(at -> -at.getRoleLabels().size()))
                .sorted(Comparator.comparing(Atom::isRuleResolvable))
                .collect(Collectors.toList());
    }

    @Override
    public Set<Var> getVarNames() {
        Set<Var> vars = super.getVarNames();
        vars.addAll(getRolePlayers());
        vars.addAll(getRoleVariables());
        return vars;
    }

    @Override
    public Set<Var> getRoleExpansionVariables(){
        return getRelationPlayers().stream()
                .map(RelationPlayer::getRole)
                .flatMap(CommonUtil::optionalToStream)
                .filter(p -> p.var().isUserDefinedName())
                .filter(p -> !p.getTypeLabel().isPresent())
                .map(VarPatternAdmin::var)
                .collect(Collectors.toSet());
    }

    private Set<Var> getSpecificRolePlayers() {
        return getRoleVarMap().entries().stream()
                .filter(e -> !Schema.MetaSchema.isMetaLabel(e.getKey().getLabel()))
                .map(Map.Entry::getValue)
                .collect(toSet());
    }

    @Override
    public Set<TypeAtom> getSpecificTypeConstraints() {
        Set<Var> mappedVars = getSpecificRolePlayers();
        return getTypeConstraints()
                .filter(t -> mappedVars.contains(t.getVarName()))
                .filter(t -> Objects.nonNull(t.getSchemaConcept()))
                .collect(toSet());
    }

    @Override
    public Stream<Predicate> getInnerPredicates(){
        return Stream.concat(
                super.getInnerPredicates(),
                getRelationPlayers().stream()
                        .map(RelationPlayer::getRole)
                        .flatMap(CommonUtil::optionalToStream)
                        .filter(vp -> vp.var().isUserDefinedName())
                        .map(vp -> new Pair<>(vp.var(), vp.getTypeLabel().orElse(null)))
                        .filter(p -> Objects.nonNull(p.getValue()))
                        .map(p -> new IdPredicate(p.getKey(), p.getValue(), getParentQuery()))
        );
    }

    /**
     * attempt to infer role types of this relation and return a fresh relationship with inferred role types
     * @return either this if nothing/no roles can be inferred or fresh relation with inferred role types
     */
    private RelationshipAtom inferRoles(Answer sub){
        //return if all roles known and non-meta
        Set<Role> explicitRoleTypes = getExplicitRoleTypes();
        boolean allRolesMeta = explicitRoleTypes.stream().filter(role -> Schema.MetaSchema.isMetaLabel(role.getLabel())).count() == getRelationPlayers().size();
        boolean metaRoleRecomputationViable = allRolesMeta && !sub.isEmpty();
        if (explicitRoleTypes.size() == getRelationPlayers().size() && !metaRoleRecomputationViable) return this;

        GraknTx graph = getParentQuery().tx();
        Role metaRole = graph.admin().getMetaRole();
        RelationshipType relType = getSchemaConcept() != null? getSchemaConcept().asRelationshipType() : null;

        List<RelationPlayer> allocatedRelationPlayers = new ArrayList<>();

        //explicit role types from castings
        List<Pair<Var, VarPattern>> rolePlayerMappings = new ArrayList<>();
        getRelationPlayers().forEach(rp -> {
            Var varName = rp.getRolePlayer().var();
            VarPatternAdmin rolePattern = rp.getRole().orElse(null);
            if (rolePattern != null) {
                rolePlayerMappings.add(new Pair<>(varName, rolePattern));
                allocatedRelationPlayers.add(rp);
            }
        });

        //remaining roles
        //role types can repeat so no matter what has been allocated still the full spectrum of possibilities is present
        //TODO make restrictions based on cardinality constraints
        Set<Role> possibleRoles = relType != null? relType.relates().collect(toSet()) : Sets.newHashSet(metaRole);

        //possible role types for each casting based on its type
        Map<RelationPlayer, Set<Role>> mappings = new HashMap<>();
        Map<Var, SchemaConcept> varSchemaConceptMap = getParentQuery().getVarSchemaConceptMap();

        //types deduced from substitution
        inferEntityTypes(sub).forEach(p -> varSchemaConceptMap.put(p.getKey(), p.getValue()));

        getRelationPlayers().stream()
                .filter(rp -> !allocatedRelationPlayers.contains(rp))
                .forEach(casting -> {
                    Var varName = casting.getRolePlayer().var();
                    SchemaConcept schemaConcept = varSchemaConceptMap.get(varName);
                    if (schemaConcept != null && !Schema.MetaSchema.isMetaLabel(schemaConcept.getLabel()) && schemaConcept.isType()) {
                        mappings.put(casting, ReasonerUtils.getCompatibleRoleTypes(schemaConcept.asType(), possibleRoles.stream()));
                    } else {
                        mappings.put(casting, ReasonerUtils.getSchemaConcepts(possibleRoles));
                    }
                });


        //resolve ambiguities until no unambiguous mapping exist
        while( mappings.values().stream().filter(s -> s.size() == 1).count() != 0) {
            Map.Entry<RelationPlayer, Set<Role>> entry = mappings.entrySet().stream()
                    .filter(e -> e.getValue().size() == 1)
                    .findFirst().orElse(null);

            RelationPlayer casting = entry.getKey();
            Var varName = casting.getRolePlayer().var();
            Role role = entry.getValue().iterator().next();
            VarPatternAdmin roleVar = Graql.var().label(role.getLabel()).admin();

            //TODO remove from all mappings if it follows from cardinality constraints
            mappings.get(casting).remove(role);

            rolePlayerMappings.add(new Pair<>(varName, roleVar));
            allocatedRelationPlayers.add(casting);
        }

        //fill in unallocated roles with metarole
        getRelationPlayers().stream()
                .filter(rp -> !allocatedRelationPlayers.contains(rp))
                .forEach(rp -> {
                    Var varName = rp.getRolePlayer().var();
                    VarPatternAdmin rolePattern = rp.getRole().orElse(null);
                    if (rolePattern != null && rolePattern.var().isUserDefinedName()){
                        rolePlayerMappings.add(new Pair<>(varName, rolePattern.var().asUserDefined().label(metaRole.getLabel())));
                    } else{
                        rolePlayerMappings.add(new Pair<>(varName, Graql.var().label(metaRole.getLabel())));
                    }
                });

        PatternAdmin newPattern = constructRelationVarPattern(getVarName(), getPredicateVariable(), rolePlayerMappings);
        return new RelationshipAtom(newPattern.asVarPattern(), getPredicateVariable(), getTypePredicate(), getParentQuery());
    }

    /**
     * @return map containing roleType - (rolePlayer var - rolePlayer type) pairs
     */
    private Multimap<Role, Var> computeRoleVarMap() {
        Multimap<Role, Var> roleMap = ArrayListMultimap.create();
        if (getParentQuery() == null || getSchemaConcept() == null) return roleMap;

        GraknTx graph = getParentQuery().tx();
        getRelationPlayers().forEach(c -> {
            Var varName = c.getRolePlayer().var();
            VarPatternAdmin role = c.getRole().orElse(null);
            if (role != null) {
                //try directly
                Label typeLabel = role.getTypeLabel().orElse(null);
                Role roleType = typeLabel != null ? graph.getRole(typeLabel.getValue()) : null;
                //try indirectly
                if (roleType == null && role.var().isUserDefinedName()) {
                    IdPredicate rolePredicate = getIdPredicate(role.var());
                    if (rolePredicate != null) roleType = graph.getConcept(rolePredicate.getPredicate());
                }
                if (roleType != null) roleMap.put(roleType, varName);
            }
        });
        return roleMap;
    }

    public Multimap<Role, Var> getRoleVarMap() {
        if (roleVarMap == null){
            roleVarMap = computeRoleVarMap();
        }
        return roleVarMap;
    }

    private Multimap<Role, RelationPlayer> getRoleRelationPlayerMap(){
        Multimap<Role, RelationPlayer> roleRelationPlayerMap = ArrayListMultimap.create();
        Multimap<Role, Var> roleVarMap = getRoleVarMap();
        List<RelationPlayer> relationPlayers = getRelationPlayers();
        roleVarMap.asMap().entrySet()
                .forEach(e -> {
                    Role role = e.getKey();
                    Label roleLabel = role.getLabel();
                    relationPlayers.stream()
                            .filter(rp -> rp.getRole().isPresent())
                            .forEach(rp -> {
                                VarPatternAdmin roleTypeVar = rp.getRole().orElse(null);
                                Label rl = roleTypeVar != null ? roleTypeVar.getTypeLabel().orElse(null) : null;
                                if (roleLabel != null && roleLabel.equals(rl)) {
                                    roleRelationPlayerMap.put(role, rp);
                                }
                            });
                });
        return roleRelationPlayerMap;
    }

    private Set<List<Pair<RelationPlayer, RelationPlayer>>> getRelationPlayerMappings(RelationshipAtom parentAtom) {
        return getRelationPlayerMappings(parentAtom, false);
    }

    /**
     * @param parentAtom reference atom defining the mapping
     * @return set of possible COMPLETE mappings between this (child) and parent relation players
     */
    private Set<List<Pair<RelationPlayer, RelationPlayer>>> getRelationPlayerMappings(RelationshipAtom parentAtom, boolean exact) {
        Multimap<Role, RelationPlayer> childRoleRPMap = getRoleRelationPlayerMap();
        Map<Var, SchemaConcept> parentVarSchemaConceptMap = parentAtom.getParentQuery().getVarSchemaConceptMap();
        Map<Var, SchemaConcept> childVarSchemaConceptMap = this.getParentQuery().getVarSchemaConceptMap();

        //establish compatible castings for each parent casting
        List<Set<Pair<RelationPlayer, RelationPlayer>>> compatibleMappingsPerParentRP = new ArrayList<>();
        ReasonerQueryImpl childQuery = (ReasonerQueryImpl) getParentQuery();
        Set<Role> childRoles = childRoleRPMap.keySet();
        parentAtom.getRelationPlayers().stream()
                .filter(prp -> prp.getRole().isPresent())
                .forEach(prp -> {
                    VarPatternAdmin parentRolePattern = prp.getRole().orElse(null);
                    if (parentRolePattern == null){
                        throw GraqlQueryException.rolePatternAbsent(this);
                    }
                    Label parentRoleLabel = parentRolePattern.getTypeLabel().orElse(null);

                    if (parentRoleLabel != null) {
                        Var parentRolePlayer = prp.getRolePlayer().var();
                        SchemaConcept parentType = parentVarSchemaConceptMap.get(parentRolePlayer);

                        Set<Role> compatibleChildRoles = playableRoles(
                                tx().getSchemaConcept(parentRoleLabel),
                                parentType,
                                childRoles);

                        List<RelationPlayer> compatibleRelationPlayers = new ArrayList<>();
                        compatibleChildRoles.stream()
                                .filter(childRoleRPMap::containsKey)
                                .forEach(role -> {
                                    childRoleRPMap.get(role).stream()
                                            //check for inter-type compatibility
                                            .filter(crp -> {
                                                Var childVar = crp.getRolePlayer().var();
                                                SchemaConcept childType = childVarSchemaConceptMap.get(childVar);

                                                if (exact) return childQuery.isTypeRoleCompatible(childVar, parentType) && !areDisjointTypes(parentType, childType);
                                                else return childQuery.isTypeRoleCompatible(childVar, parentType)
                                                        && (childType == null || !areDisjointTypes(parentType, childType));

                                            })
                                            //check for substitution compatibility
                                            .filter(crp -> {
                                                IdPredicate parentId = parentAtom.getPredicates(IdPredicate.class)
                                                        .filter(p -> p.getVarName().equals(prp.getRolePlayer().var()))
                                                        .findFirst().orElse(null);
                                                IdPredicate childId = this.getPredicates(IdPredicate.class)
                                                        .filter(p -> p.getVarName().equals(crp.getRolePlayer().var()))
                                                        .findFirst().orElse(null);

                                                if (exact) return parentId == null || parentId.isEquivalent(childId);
                                                else return childId == null || parentId == null || parentId.isEquivalent(childId);
                                            })
                                            .forEach(compatibleRelationPlayers::add);
                                });
                        if (!compatibleRelationPlayers.isEmpty()) {
                            compatibleMappingsPerParentRP.add(
                                    compatibleRelationPlayers.stream()
                                            .map(crp -> new Pair<>(crp, prp))
                                            .collect(Collectors.toSet())
                            );
                        }
                    } else {
                        compatibleMappingsPerParentRP.add(
                                getRelationPlayers().stream()
                                        .map(crp -> new Pair<>(crp, prp))
                                        .collect(Collectors.toSet())
                        );
                    }
                });

        return Sets.cartesianProduct(compatibleMappingsPerParentRP).stream()
                .filter(list -> !list.isEmpty())
                //check the same child rp is not mapped to multiple parent rps
                .filter(list -> {
                    List<RelationPlayer> listChildRps = list.stream().map(Pair::getKey).collect(Collectors.toList());
                    //NB: this preserves cardinality instead of removing all occuring instances which is what we want
                    return ReasonerUtils.subtract(listChildRps, this.getRelationPlayers()).isEmpty();
                })
                //check all parent rps mapped
                .filter(list -> {
                    List<RelationPlayer> listParentRps = list.stream().map(Pair::getValue).collect(Collectors.toList());
                    return listParentRps.containsAll(parentAtom.getRelationPlayers());
                })
                .collect(toSet());
    }

    @Override
    public Unifier getUnifier(Atom pAtom){
        return getMultiUnifier(pAtom, true).getUnifier();
    }

    @Override
    public MultiUnifier getMultiUnifier(Atom pAtom, boolean exact) {
        if (this.equals(pAtom))  return new MultiUnifierImpl();

        Unifier baseUnifier = super.getUnifier(pAtom);
        Set<Unifier> unifiers = new HashSet<>();
        if (pAtom.isRelation()) {
            assert(pAtom instanceof RelationshipAtom); // This is safe due to the check above
            RelationshipAtom parentAtom = (RelationshipAtom) pAtom;

            boolean unifyRoleVariables = parentAtom.getRelationPlayers().stream()
                    .map(RelationPlayer::getRole)
                    .flatMap(CommonUtil::optionalToStream)
                    .filter(rp -> rp.var().isUserDefinedName())
                    .findFirst().isPresent();
            getRelationPlayerMappings(parentAtom, exact)
                    .forEach(mappings -> {
                        Unifier unifier = new UnifierImpl(baseUnifier);
                        mappings.forEach(rpm -> {
                            //add role player mapping
                            unifier.addMapping(rpm.getKey().getRolePlayer().var(), rpm.getValue().getRolePlayer().var());

                            //add role var mapping if needed
                            VarPattern childRolePattern = rpm.getKey().getRole().orElse(null);
                            VarPattern parentRolePattern = rpm.getValue().getRole().orElse(null);
                            if (parentRolePattern != null && childRolePattern != null && unifyRoleVariables){
                                unifier.addMapping(childRolePattern.admin().var(), parentRolePattern.admin().var());
                            }

                        });
                        unifiers.add(unifier);
                    });
        } else {
            unifiers.add(baseUnifier);
        }
        return new MultiUnifierImpl(unifiers);
    }

    /**
     * if any {@link Role} variable of the parent is user defined rewrite ALL {@link Role} variables to user defined (otherwise unification is problematic)
     * @param parentAtom parent atom that triggers rewrite
     * @return new relation atom with user defined {@link Role} variables if necessary or this
     */
    private RelationshipAtom rewriteWithVariableRoles(Atom parentAtom){
        if (!parentAtom.requiresRoleExpansion()) return this;

        VarPattern relVar = getPattern().asVarPattern().getProperty(IsaProperty.class)
                .map(prop -> getVarName().isa(prop.type())).orElse(getVarName());

        for (RelationPlayer rp: getRelationPlayers()) {
            VarPatternAdmin rolePattern = rp.getRole().orElse(null);
            if (rolePattern != null) {
                Var roleVar = rolePattern.var();
                Label roleLabel = rolePattern.getTypeLabel().orElse(null);
                relVar = relVar.rel(roleVar.asUserDefined().label(roleLabel), rp.getRolePlayer());
            } else {
                relVar = relVar.rel(rp.getRolePlayer());
            }
        }
        return new RelationshipAtom(relVar.admin(), getPredicateVariable(), getTypePredicate(), getParentQuery());
    }

    /**
     * @param parentAtom parent atom that triggers rewrite
     * @return new relation atom with user defined name if necessary or this
     */
    private RelationshipAtom rewriteWithRelationVariable(Atom parentAtom){
        if (!parentAtom.getVarName().isUserDefinedName()) return this;

        VarPattern newVar = Graql.var().asUserDefined();
        VarPattern relVar = getPattern().asVarPattern().getProperty(IsaProperty.class)
                .map(prop -> newVar.isa(prop.type()))
                .orElse(newVar);

        for (RelationPlayer c: getRelationPlayers()) {
            VarPatternAdmin roleType = c.getRole().orElse(null);
            if (roleType != null) {
                relVar = relVar.rel(roleType, c.getRolePlayer());
            } else {
                relVar = relVar.rel(c.getRolePlayer());
            }
        }
        return new RelationshipAtom(relVar.admin(), getPredicateVariable(), getTypePredicate(), getParentQuery());
    }

    @Override
    public Atom rewriteToUserDefined(Atom parentAtom){
        return this
                .rewriteWithRelationVariable(parentAtom)
                .rewriteWithVariableRoles(parentAtom);
    }
}