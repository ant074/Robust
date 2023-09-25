package com.meituan.robust.autopatch

import com.meituan.robust.utils.JavaUtils
import javassist.CannotCompileException
import javassist.CtClass
import javassist.CtMethod
import javassist.expr.ExprEditor
import javassist.expr.MethodCall

class InlineClassFactory {
    private HashMap<String, List<String>> classInLineMethodsMap = new HashMap<>();
    private static InlineClassFactory inlineClassFactory = new InlineClassFactory();

    private InlineClassFactory() {

    }

    public static void init() {
        inlineClassFactory = new InlineClassFactory();;
    }
    public static Set getAllInLineMethodLongname() {
        Set<String> set=new HashSet<>();
        for(String key:inlineClassFactory.classInLineMethodsMap.keySet()){
            set.addAll(inlineClassFactory.classInLineMethodsMap.get(key));
        }
        return set;
    }

    def dealInLineClass(String patchPath) {
        System.out.println("dealInLineClass a")
        //pay attention to order
        Set usedClass = new HashSet();
        usedClass.addAll(Config.newlyAddedClassNameList);
        System.out.println("dealInLineClass b")
        Set newlyAddedClassInlineSet = getAllInlineClasses(usedClass, null);
        System.out.println("dealInLineClass c")
        usedClass.addAll(newlyAddedClassInlineSet);
        usedClass.addAll(Config.modifiedClassNameList)
        System.out.println("dealInLineClass d")
        Set inLineClassNameSet = getAllInlineClasses(usedClass, Config.patchMethodSignatureSet);
        System.out.println("dealInLineClass e")
        inLineClassNameSet.removeAll(newlyAddedClassInlineSet)
        System.out.println("dealInLineClass f")
        inLineClassNameSet.addAll(classInLineMethodsMap.keySet())
        System.out.println("dealInLineClass g")
        //all inline patch class
        createHookInlineClass(inLineClassNameSet)
        System.out.println("dealInLineClass h")
        //针对修改类的linepatch
        for (String fullClassName : inLineClassNameSet) {
            System.out.println("dealInLineClass i:"+fullClassName)
            CtClass inlineClass = Config.classPool.get(fullClassName);
            System.out.println("dealInLineClass j")
            List<String> inlineMethod = classInLineMethodsMap.getOrDefault(fullClassName, new ArrayList<String>());
            System.out.println("dealInLineClass k")
            CtClass inlinePatchClass = PatchesFactory.createPatch(patchPath, inlineClass, true, NameManger.getInstance().getInlinePatchName(inlineClass.name), inlineMethod.toSet())
            System.out.println("dealInLineClass l")
            inlinePatchClass.writeFile(patchPath)
            System.out.println("dealInLineClass m")
        }
    }


    def dealInLineMethodInNewAddClass(String patchPath, List newAddClassList) {
        System.out.println("dealInLineMethodInNewAddClass 1")
        for (String fullClassName : newAddClassList) {
            System.out.println("dealInLineMethodInNewAddClass 2："+fullClassName)
            CtClass newlyAddClass = Config.classPool.get(fullClassName);
            System.out.println("dealInLineMethodInNewAddClass 3")
            newlyAddClass.defrost();
            System.out.println("dealInLineMethodInNewAddClass 4")
            newlyAddClass.declaredMethods.each { method ->
                System.out.println("dealInLineMethodInNewAddClass 5:"+method)
                method.instrument(new ExprEditor() {
                    public void edit(MethodCall m) throws CannotCompileException {
                        System.out.println("dealInLineMethodInNewAddClass 6:"+method)
                        repalceInlineMethod(m, method, true);
                        System.out.println("dealInLineMethodInNewAddClass 7 end")
                    }
                })
            }
            System.out.println("dealInLineMethodInNewAddClass 8")
            newlyAddClass.writeFile(patchPath);
            System.out.println("dealInLineMethodInNewAddClass 9")
        }
    }

    def createHookInlineClass(Set inLineClassNameSet) {
        System.out.println("createHookInlineClass 1")
        for (String fullClassName : inLineClassNameSet) {
            System.out.println("createHookInlineClass 2:"+fullClassName)
            CtClass inlineClass = Config.classPool.get(fullClassName);
            System.out.println("createHookInlineClass 3:"+inlineClass)
            CtClass inlinePatchClass = PatchesFactory.cloneClass(inlineClass, NameManger.getInstance().getInlinePatchName(inlineClass.name), null)
            System.out.println("createHookInlineClass 4:"+inlinePatchClass)
            inlinePatchClass = JavaUtils.addPatchConstruct(inlinePatchClass, inlineClass)
            System.out.println("createHookInlineClass 5:"+inlinePatchClass)
            PatchesFactory.createPublicMethodForPrivate(inlinePatchClass)
            System.out.println("createHookInlineClass 6")
        }
    }
/***
 *
 * @param usedClass
 * @param patchMethodSignureSet 只查找指定的方法体来确认内联类，如果全部的类则传递null
 * @return
 */
    def Set getAllInlineClasses(Set usedClass, Set patchMethodSignureSet) {
        HashSet temInLineFirstSet = initInLineClass(usedClass, patchMethodSignureSet);
        HashSet temInLineSecondSet = initInLineClass(temInLineFirstSet, patchMethodSignureSet);
        temInLineSecondSet.addAll(temInLineFirstSet);
        //第一次temInLineFirstSet要和temInLineSecondSet第二次获取的内联类数量相同，则表明找出了所有的内联类
        while ((temInLineFirstSet.size() < temInLineSecondSet.size())) {
            temInLineFirstSet.addAll(initInLineClass(temInLineSecondSet, patchMethodSignureSet));
            //这个循环有点饶人，initInLineClass返回的是temInLineListSecond中所有的内联类
            temInLineSecondSet.addAll(initInLineClass(temInLineFirstSet, patchMethodSignureSet));
        }

        return temInLineSecondSet;
    }

    public static void dealInLineClass(String patchPath, List list) {
        System.out.println("dealInLineClass patchPath="+patchPath)
        inlineClassFactory.dealInLineClass(patchPath);
        System.out.println("dealInLineClass 2")
        inlineClassFactory.dealInLineMethodInNewAddClass(patchPath, list);
        System.out.println("dealInLineClass 3")
    }
    /**
     *
     * @param classNameList is modified class List
     * @return all inline classes used in classNameList
     */
    def HashSet initInLineClass(Set classNamesSet, Set patchMethodSignureSet) {
        System.out.println("initInLineClass1")
        HashSet inLineClassNameSet = new HashSet<String>();
        CtClass modifiedCtclass;
        Set <String>allPatchMethodSignureSet = new HashSet();
        boolean isNewClass=false;
        System.out.println("initInLineClass2")
        for (String fullClassName : classNamesSet) {
            if(patchMethodSignureSet!=null) {
                allPatchMethodSignureSet.addAll(patchMethodSignureSet);
            } else{
                isNewClass=true;
            }
            System.out.println("initInLineClass3："+fullClassName)
            modifiedCtclass = Config.classPool.get(fullClassName)
            modifiedCtclass.declaredMethods.each {
                method ->
                    System.out.println("initInLineClass4："+method)
                    //找出modifiedclass中所有内联的类
                    allPatchMethodSignureSet.addAll(classInLineMethodsMap.getOrDefault(fullClassName, new ArrayList()))
                    if (isNewClass||allPatchMethodSignureSet.contains(method.longName)) {
//                        isNewClass=false;
                        method.instrument(new ExprEditor() {
                            @Override
                            void edit(MethodCall m) throws CannotCompileException {
                                System.out.println("initInLineClass5："+m.method.declaringClass.name)
                                List inLineMethodList = classInLineMethodsMap.getOrDefault(m.method.declaringClass.name, new ArrayList());
                                System.out.println("initInLineClass6")
                                ClassMapping classMapping = ReadMapping.getInstance().getClassMapping(m.method.declaringClass.name);
                                System.out.println("initInLineClass7")
                                if (null != classMapping && classMapping.memberMapping.get(ReflectUtils.getJavaMethodSignureWithReturnType(m.method)) == null) {
                                        inLineClassNameSet.add(m.method.declaringClass.name);
                                    System.out.println("initInLineClass8")
                                    if (!inLineMethodList.contains(m.method.longName)) {
                                        System.out.println("initInLineClass9:"+m.method.longName)
                                        inLineMethodList.add(m.method.longName);
                                        classInLineMethodsMap.put(m.method.declaringClass.name, inLineMethodList)
                                        System.out.println("initInLineClass10")
                                    }
                                }
                            }
                        }
                        )
                    }
            }
        }
        return inLineClassNameSet;
    }


    def repalceInlineMethod(MethodCall m, CtMethod method, boolean isNewClass) {
        ClassMapping classMapping = ReadMapping.getInstance().getClassMapping(m.method.declaringClass.name);
        if (null != classMapping && classMapping.memberMapping.get(ReflectUtils.getJavaMethodSignureWithReturnType(m.method)) == null) {
            m.replace(ReflectUtils.getInLineMemberString(m.method, ReflectUtils.isStatic(method.modifiers), isNewClass));
            return true;
        }
        return false;
    }


}
